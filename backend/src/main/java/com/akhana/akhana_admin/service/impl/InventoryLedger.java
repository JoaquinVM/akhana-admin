package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.model.ProductStock;
import com.akhana.akhana_admin.model.StockAdjustmentReason;
import com.akhana.akhana_admin.model.StockMovement;
import com.akhana.akhana_admin.model.StockMovementType;
import com.akhana.akhana_admin.repository.ProductStockRepository;
import com.akhana.akhana_admin.repository.StockMovementRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * INV-03, INV-06, T-04: writes the valued kardex. Each row carries its quantity and value, and the stock shortcut
 * (product_stocks) moves by exactly that amount, so the balance is always the sum of the rows. The rules of the weighted
 * average live here:
 * <ul>
 *   <li>an entry mixes its cost with what there is; with the stock at zero or below, it fixes the average: an «Ajuste
 *   del promedio» row first takes what there is to the entry's cost;</li>
 *   <li>an exit leaves at the cost of its line, the average of the moment rounded to the cent;</li>
 *   <li>a change of value only (a cost or shipping after the delivery, INV-07) goes to what is left or, with nothing
 *   left, to the cost of sales.</li>
 * </ul>
 * Callers lock the stock rows first ({@link #lock}), last in the lock order (T-14).
 */
@Component
@RequiredArgsConstructor
public class InventoryLedger {

    private static final BigDecimal ZERO_QTY = BigDecimal.ZERO.setScale(3);
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);

    private final ProductStockRepository productStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /** What a row points to and says, besides its amounts. */
    @Builder
    public record Source(String reference, String comment, StockAdjustmentReason reason, UUID saleItemId,
                         UUID consumptionItemId, UUID purchaseItemId, UUID stockCountLineId, String username) {
    }

    /** T-14: the stock rows of these products, locked together and by id; a missing row is created, still to count. */
    public Map<UUID, ProductStock> lock(Collection<UUID> productIds) {
        Map<UUID, ProductStock> stocks = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return stocks;
        }
        TreeSet<UUID> ids = new TreeSet<>(productIds);
        productStockRepository.findAllByIdForUpdate(ids).forEach(s -> stocks.put(s.getProductId(), s));
        for (UUID id : ids) {
            if (!stocks.containsKey(id)) {
                // Inserted by this transaction, so nobody else sees it until it commits
                stocks.put(id, productStockRepository.saveAndFlush(ProductStock.builder().productId(id).build()));
            }
        }
        return stocks;
    }

    /** An entry of quantity > 0 with its value (its cost × quantity), mixed into the average. */
    public StockMovement entry(ProductStock stock, StockMovementType type, BigDecimal quantity, BigDecimal value,
                               Source source) {
        BigDecimal qty = quantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal val = value.setScale(2, RoundingMode.HALF_UP);
        BigDecimal entryCost = val.divide(qty, 4, RoundingMode.HALF_UP);
        boolean fixesAverage = stock.getQuantity().signum() <= 0;
        if (fixesAverage) {
            // INV-06: with nothing (or less than nothing) in stock, the entry fixes the average
            BigDecimal revalued = stock.getQuantity().multiply(entryCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal reset = revalued.subtract(stock.getValue());
            if (reset.signum() != 0) {
                write(stock, StockMovementType.AVERAGE_RESET, ZERO_QTY, null, reset, ZERO_MONEY, entryCost,
                    Source.builder().reference(source.reference()).username(source.username()).build());
            }
        }
        BigDecimal newQty = stock.getQuantity().add(qty);
        BigDecimal newValue = stock.getValue().add(val);
        BigDecimal average = fixesAverage ? entryCost : averageOf(newValue, newQty, entryCost);
        return write(stock, type, qty, entryCost, val, ZERO_MONEY, average, source);
    }

    /** An exit of quantity > 0 at the cost of its line (INV-06: the average of the moment, to the cent). */
    public StockMovement exitAt(ProductStock stock, StockMovementType type, BigDecimal quantity, BigDecimal unitCost,
                                Source source) {
        BigDecimal qty = quantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal value = qty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        return exitWithValue(stock, type, qty, value, ZERO_MONEY, source);
    }

    /**
     * An exit that takes out exactly what an entry put (a purchase that goes back to pending or is voided, INV-14),
     * including what of it went to the cost of sales.
     */
    public StockMovement exitWithValue(ProductStock stock, StockMovementType type, BigDecimal quantity,
                                       BigDecimal value, BigDecimal expensed, Source source) {
        BigDecimal qty = quantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal val = value.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newQty = stock.getQuantity().subtract(qty);
        BigDecimal newValue = stock.getValue().subtract(val);
        BigDecimal unitCost = qty.signum() > 0 ? val.divide(qty, 4, RoundingMode.HALF_UP) : null;
        BigDecimal kept = stock.getAverageCost() != null ? stock.getAverageCost() : unitCost;
        BigDecimal average = newQty.signum() > 0 ? averageOf(newValue, newQty, kept) : kept;
        return write(stock, type, qty.negate(), unitCost, val.negate(), expensed.setScale(2, RoundingMode.HALF_UP).negate(),
            average, source);
    }

    /**
     * INV-07: a change of value only. With stock left it changes the value of what is left (and its average); with
     * none, it goes to the cost of sales. The value of what is left never goes below 0: the rest is cost of sales.
     */
    public StockMovement revalue(ProductStock stock, StockMovementType type, BigDecimal delta, Source source) {
        BigDecimal change = delta.setScale(2, RoundingMode.HALF_UP);
        BigDecimal toStock = ZERO_MONEY;
        if (stock.getQuantity().signum() > 0) {
            toStock = stock.getValue().add(change).signum() >= 0 ? change : stock.getValue().negate();
        }
        BigDecimal expensed = change.subtract(toStock);
        BigDecimal newValue = stock.getValue().add(toStock);
        BigDecimal average = stock.getQuantity().signum() > 0
            ? averageOf(newValue, stock.getQuantity(), stock.getAverageCost())
            : stock.getAverageCost();
        return write(stock, type, ZERO_QTY, null, toStock, expensed, average, source);
    }

    /**
     * INV-18: the initial load. The stock becomes what was counted, valued at that day's cost, which becomes the
     * average; the row carries the difference with whatever the shortcut had.
     */
    public StockMovement initialLoad(ProductStock stock, BigDecimal counted, BigDecimal cost, Source source) {
        BigDecimal qty = counted.setScale(3, RoundingMode.HALF_UP);
        BigDecimal unitCost = cost.setScale(4, RoundingMode.HALF_UP);
        BigDecimal targetValue = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP);
        StockMovement row = write(stock, StockMovementType.INITIAL_LOAD, qty.subtract(stock.getQuantity()), unitCost,
            targetValue.subtract(stock.getValue()), ZERO_MONEY, unitCost, source);
        stock.setStartedAt(row.getCreatedAt());
        return row;
    }

    private static BigDecimal averageOf(BigDecimal value, BigDecimal quantity, BigDecimal fallback) {
        return quantity.signum() > 0 ? value.divide(quantity, 4, RoundingMode.HALF_UP) : fallback;
    }

    private StockMovement write(ProductStock stock, StockMovementType type, BigDecimal quantity, BigDecimal unitCost,
                                BigDecimal value, BigDecimal expensed, BigDecimal average, Source source) {
        stock.setQuantity(stock.getQuantity().add(quantity).setScale(3, RoundingMode.HALF_UP));
        stock.setValue(stock.getValue().add(value).setScale(2, RoundingMode.HALF_UP));
        stock.setAverageCost(average);
        productStockRepository.save(stock);
        Instant now = Instant.now(clock);
        return stockMovementRepository.save(StockMovement.builder()
            .productId(stock.getProductId())
            .type(type)
            .quantity(quantity)
            .unitCost(unitCost)
            .value(value)
            .expensedValue(expensed)
            .balanceQuantity(stock.getQuantity())
            .balanceValue(stock.getValue())
            .balanceAverageCost(average)
            .reference(source.reference())
            .comment(source.comment())
            .reason(source.reason())
            .saleItemId(source.saleItemId())
            .consumptionItemId(source.consumptionItemId())
            .purchaseItemId(source.purchaseItemId())
            .stockCountLineId(source.stockCountLineId())
            .createdBy(source.username())
            .createdAt(now)
            .build());
    }
}
