package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.KardexResponse;
import com.akhana.akhana_admin.dto.StockAdjustmentRequest;
import com.akhana.akhana_admin.dto.StockItemResponse;
import com.akhana.akhana_admin.dto.StockLevelResponse;
import com.akhana.akhana_admin.exception.InsufficientStockException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.ProductStockRepository;
import com.akhana.akhana_admin.repository.PurchaseRepository;
import com.akhana.akhana_admin.repository.StockMovementRepository;
import com.akhana.akhana_admin.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryServiceImpl implements InventoryService {

    static final String STOCK_SHORT = "No hay stock registrado de %s (quedan %s). ¿Vender igual?";
    static final String NOT_CONTROLLED = "'%s' no controla stock.";
    static final String TO_COUNT = "'%s' todavía no tiene su primer conteo: se ajusta contándolo.";
    static final String ADJUSTMENT_QUANTITY = "La cantidad del ajuste debe ser mayor a 0.";
    static final String KARDEX_RANGE = "La fecha desde no puede ser posterior a la fecha hasta.";

    public static final String CONTROLLED = "CONTROLLED";
    public static final String TO_COUNT_STATUS = "TO_COUNT";
    public static final String NOT_CONTROLLED_STATUS = "NOT_CONTROLLED";

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal ZERO_QTY = BigDecimal.ZERO.setScale(3);

    private final InventoryLedger ledger;
    private final StockMovementRepository stockMovementRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final Clock clock;

    // ---------------------------------------------------------------------------------------------------------------
    // INV-10

    @Override
    public boolean checkStock(Map<Product, BigDecimal> baseQuantities, boolean confirmed) {
        List<String> short_ = new ArrayList<>();
        for (Map.Entry<Product, BigDecimal> entry : baseQuantities.entrySet()) {
            Product product = entry.getKey();
            if (!product.controlsStockNow()) {
                continue;
            }
            BigDecimal stock = product.getStockQuantity() != null ? product.getStockQuantity() : ZERO_QTY;
            if (entry.getValue().compareTo(stock) > 0) {
                short_.add(String.format(STOCK_SHORT, product.getName(), quantityText(stock, product.getSaleUnit())));
            }
        }
        if (!short_.isEmpty() && !confirmed) {
            throw new InsufficientStockException(String.join(" ", short_));
        }
        return !short_.isEmpty();
    }

    /** «3» or «0,250 kg»: what is left, in the base unit (CAT-14). */
    static String quantityText(BigDecimal quantity, SaleUnit saleUnit) {
        return saleUnit == SaleUnit.KILOGRAM
            ? StoreFormat.number(quantity, 3) + " kg"
            : quantity.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** The base quantity of a line: packs × package (CAT-15), the kilos written (INV-09) or the units. */
    static BigDecimal baseQuantityOf(BigDecimal baseQuantity, BigDecimal quantity) {
        return baseQuantity != null ? baseQuantity : quantity;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sales and consumptions

    @Override
    public void recordSale(Sale sale, String username) {
        List<SaleItem> moving = sale.getItems().stream()
            .filter(item -> item.getFreeLineType() == null && item.getProduct().controlsStockNow())
            .toList();
        if (moving.isEmpty()) {
            return;
        }
        Map<UUID, ProductStock> stocks = ledger.lock(moving.stream().map(i -> i.getProduct().getId()).toList());
        StockMovementType type = sale.getSaleType() == SaleType.AL_COSTO ? StockMovementType.AT_COST_SALE : StockMovementType.SALE;
        for (SaleItem item : moving) {
            ledger.exitAt(stocks.get(item.getProduct().getId()), type,
                baseQuantityOf(item.getBaseQuantity(), item.getQuantity()), item.getUnitCost(),
                InventoryLedger.Source.builder().reference(sale.getSaleNumber()).saleItemId(item.getId())
                    .username(username).build());
        }
    }

    @Override
    public void reverseSale(Sale sale, String username) {
        // Only a product that still controls its stock gets it back (CAT-05)
        List<UUID> itemIds = sale.getItems().stream().filter(i -> i.getProduct().controlsStockNow())
            .map(SaleItem::getId).toList();
        if (itemIds.isEmpty()) {
            return;
        }
        reverse(stockMovementRepository.findBySaleItemIdInOrderBySeqAsc(itemIds), StockMovementType.SALE_VOID,
            sale.getSaleNumber(), sale.getVoidReason(), username, (row, b) -> b.saleItemId(row.getSaleItemId()));
    }

    @Override
    public void recordAssignedLine(Sale sale, SaleItem item, Product product, BigDecimal unitCost, String username) {
        if (!product.controlsStockNow()) {
            return;
        }
        ProductStock stock = ledger.lock(List.of(product.getId())).get(product.getId());
        ledger.exitAt(stock, StockMovementType.SALE, item.getQuantity(), unitCost,
            InventoryLedger.Source.builder().reference(sale.getSaleNumber()).saleItemId(item.getId())
                .comment("Línea sin identificar asignada").username(username).build());
    }

    @Override
    public void recordConsumption(Consumption consumption, String username) {
        List<ConsumptionItem> moving = consumption.getItems().stream()
            .filter(item -> item.getProduct().controlsStockNow())
            .toList();
        if (moving.isEmpty()) {
            return;
        }
        Map<UUID, ProductStock> stocks = ledger.lock(moving.stream().map(i -> i.getProduct().getId()).toList());
        for (ConsumptionItem item : moving) {
            ledger.exitAt(stocks.get(item.getProduct().getId()), StockMovementType.CONSUMPTION,
                baseQuantityOf(item.getBaseQuantity(), item.getQuantity()), item.getUnitCost(),
                InventoryLedger.Source.builder().reference(consumption.getConsumptionNumber())
                    .consumptionItemId(item.getId()).username(username).build());
        }
    }

    @Override
    public void reverseConsumption(Consumption consumption, String username) {
        List<UUID> itemIds = consumption.getItems().stream().filter(i -> i.getProduct().controlsStockNow())
            .map(ConsumptionItem::getId).toList();
        if (itemIds.isEmpty()) {
            return;
        }
        reverse(stockMovementRepository.findByConsumptionItemIdInOrderBySeqAsc(itemIds), StockMovementType.CONSUMPTION_VOID,
            consumption.getConsumptionNumber(), consumption.getVoidReason(), username,
            (row, b) -> b.consumptionItemId(row.getConsumptionItemId()));
    }

    // RG-03: what a document took goes back at the cost it left at
    private void reverse(List<StockMovement> rows, StockMovementType type, String reference, String comment,
                         String username,
                         java.util.function.BiFunction<StockMovement, InventoryLedger.Source.SourceBuilder, InventoryLedger.Source.SourceBuilder> link) {
        Map<UUID, BigDecimal[]> byLine = new LinkedHashMap<>();
        Map<UUID, StockMovement> firstRow = new LinkedHashMap<>();
        for (StockMovement row : rows) {
            UUID line = Objects.requireNonNullElse(row.getSaleItemId(), row.getConsumptionItemId());
            byLine.computeIfAbsent(line, k -> new BigDecimal[]{ZERO_QTY, ZERO_MONEY});
            byLine.get(line)[0] = byLine.get(line)[0].add(row.getQuantity());
            byLine.get(line)[1] = byLine.get(line)[1].add(row.getValue());
            firstRow.putIfAbsent(line, row);
        }
        byLine.entrySet().removeIf(e -> e.getValue()[0].signum() >= 0);
        if (byLine.isEmpty()) {
            return;
        }
        Map<UUID, ProductStock> stocks = ledger.lock(byLine.keySet().stream()
            .map(line -> firstRow.get(line).getProductId()).toList());
        for (Map.Entry<UUID, BigDecimal[]> entry : byLine.entrySet()) {
            StockMovement row = firstRow.get(entry.getKey());
            ledger.entry(stocks.get(row.getProductId()), type, entry.getValue()[0].negate(), entry.getValue()[1].negate(),
                link.apply(row, InventoryLedger.Source.builder()).reference(reference).comment(comment)
                    .username(username).build());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Purchases (INV-07, INV-14, INV-15)

    @Override
    public void syncPurchase(Purchase purchase, boolean wasDelivered, Collection<PurchaseItem> addedLines, String comment,
                             String username) {
        if (purchase.isHistorical()) {
            return;
        }
        boolean delivered = purchase.getStatus() != PurchaseStatus.ANULADO
            && purchase.getDeliveryStatus() == DeliveryStatus.ENTREGADO;
        Map<PurchaseItem, BigDecimal> shares = shippingShares(purchase);
        List<UUID> itemIds = purchase.getItems().stream().map(PurchaseItem::getId).toList();
        Map<UUID, List<StockMovement>> recorded = stockMovementRepository.findByPurchaseItemIdInOrderBySeqAsc(itemIds)
            .stream().collect(Collectors.groupingBy(StockMovement::getPurchaseItemId, LinkedHashMap::new, Collectors.toList()));
        Set<PurchaseItem> added = Collections.newSetFromMap(new IdentityHashMap<>());
        added.addAll(addedLines);

        List<PurchaseItem> moving = new ArrayList<>();
        for (PurchaseItem line : purchase.getItems()) {
            boolean hasRows = recorded.containsKey(line.getId());
            boolean entersNow = delivered && !line.isRemoved() && (!wasDelivered || added.contains(line));
            if (line.getProduct().controlsStockNow() && (hasRows || entersNow)) {
                moving.add(line);
            }
        }
        if (moving.isEmpty()) {
            return;
        }
        Map<UUID, ProductStock> stocks = ledger.lock(moving.stream().map(l -> l.getProduct().getId()).toList());
        for (PurchaseItem line : moving) {
            ProductStock stock = stocks.get(line.getProduct().getId());
            BigDecimal targetQty = delivered && !line.isRemoved()
                ? baseQuantityOf(line.getBaseQuantity(), line.getQuantity()).setScale(3, RoundingMode.HALF_UP)
                : ZERO_QTY;
            BigDecimal targetValue = targetQty.signum() > 0
                ? line.getSubtotal().add(shares.getOrDefault(line, ZERO_MONEY))
                : ZERO_MONEY;
            BigDecimal recQty = ZERO_QTY;
            BigDecimal recValue = ZERO_MONEY;
            BigDecimal recExpensed = ZERO_MONEY;
            for (StockMovement row : recorded.getOrDefault(line.getId(), List.of())) {
                recQty = recQty.add(row.getQuantity());
                recValue = recValue.add(row.getValue());
                recExpensed = recExpensed.add(row.getExpensedValue());
            }
            String why = comment;
            if (targetQty.signum() == 0) {
                why = purchase.getStatus() == PurchaseStatus.ANULADO ? "Pedido anulado"
                    : line.isRemoved() ? "Línea quitada del pedido" : "Pedido vuelto a pendiente";
            }
            InventoryLedger.Source source = InventoryLedger.Source.builder().reference(purchase.getPurchaseNumber())
                .purchaseItemId(line.getId()).comment(why).username(username).build();
            applyPurchaseDifference(stock, targetQty, targetValue, recQty, recValue, recExpensed, source);
        }
    }

    private void applyPurchaseDifference(ProductStock stock, BigDecimal targetQty, BigDecimal targetValue,
                                         BigDecimal recQty, BigDecimal recValue, BigDecimal recExpensed,
                                         InventoryLedger.Source source) {
        BigDecimal recTotal = recValue.add(recExpensed);
        if (recQty.signum() == 0 && recTotal.signum() == 0) {
            if (targetQty.signum() > 0) {
                ledger.entry(stock, StockMovementType.PURCHASE, targetQty, targetValue, source);
            }
            return;
        }
        if (targetQty.signum() == 0) {
            // INV-14: back to pending or voided: out exactly what it put, even if the stock goes below 0
            ledger.exitWithValue(stock, StockMovementType.PURCHASE_REVERSAL, recQty, recValue, recExpensed, source);
            return;
        }
        // COM-11 with INV-14: only the difference; first the quantity at the line's cost, then the value left
        BigDecimal deltaQty = targetQty.subtract(recQty);
        BigDecimal quantityValue = ZERO_MONEY;
        if (deltaQty.signum() > 0) {
            quantityValue = deltaQty.multiply(targetValue).divide(targetQty, 2, RoundingMode.HALF_UP);
            ledger.entry(stock, StockMovementType.PURCHASE_CORRECTION, deltaQty, quantityValue, source);
        } else if (deltaQty.signum() < 0) {
            BigDecimal out = deltaQty.negate();
            BigDecimal outValue = recQty.signum() > 0
                ? out.multiply(recTotal).divide(recQty, 2, RoundingMode.HALF_UP)
                : ZERO_MONEY;
            ledger.exitWithValue(stock, StockMovementType.PURCHASE_CORRECTION, out, outValue, ZERO_MONEY, source);
            quantityValue = outValue.negate();
        }
        BigDecimal valueLeft = targetValue.subtract(recTotal).subtract(quantityValue);
        if (valueLeft.signum() != 0) {
            ledger.revalue(stock, StockMovementType.PURCHASE_CORRECTION, valueLeft, source);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<PurchaseItem, BigDecimal> shippingShares(Purchase purchase) {
        Map<PurchaseItem, BigDecimal> shares = new IdentityHashMap<>();
        List<PurchaseItem> active = purchase.getItems().stream().filter(l -> !l.isRemoved()).toList();
        BigDecimal shipping = purchase.getShippingCost() != null ? purchase.getShippingCost() : ZERO_MONEY;
        BigDecimal total = active.stream().map(PurchaseItem::getSubtotal).reduce(ZERO_MONEY, BigDecimal::add);
        if (shipping.signum() <= 0 || total.signum() <= 0) {
            return shares;
        }
        PurchaseItem largest = null;
        BigDecimal given = ZERO_MONEY;
        for (PurchaseItem line : active) {
            BigDecimal share = shipping.multiply(line.getSubtotal()).divide(total, 2, RoundingMode.HALF_UP);
            shares.put(line, share);
            given = given.add(share);
            if (largest == null || line.getSubtotal().compareTo(largest.getSubtotal()) > 0) {
                largest = line;
            }
        }
        // The rounding goes to the largest line, so the shares add up to the shipping exactly
        shares.put(largest, shares.get(largest).add(shipping.subtract(given)));
        return shares;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Products (CAT-05)

    @Override
    public void startNewProduct(Product product) {
        boolean starts = product.isStockControlled() && !product.isSpecial();
        Instant now = Instant.now(clock);
        productStockRepository.save(ProductStock.builder().productId(product.getId()).startedAt(starts ? now : null).build());
        product.setStockQuantity(ZERO_QTY);
        product.setStockValue(ZERO_MONEY);
        product.setStockStartedAt(starts ? now : null);
    }

    @Override
    public void stockControlChanged(Product product, boolean controlled) {
        ProductStock stock = ledger.lock(List.of(product.getId())).get(product.getId());
        // Stopping keeps the kardex; starting again waits for the next count, which sets the stock (INV-18)
        if (!controlled) {
            stock.setStartedAt(null);
            productStockRepository.save(stock);
            product.setStockStartedAt(null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasMovements(UUID productId) {
        return stockMovementRepository.existsByProductId(productId);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Screens

    public static String statusOf(Product product) {
        if (!product.isStockControlled() || product.isSpecial()) {
            return NOT_CONTROLLED_STATUS;
        }
        return product.getStockStartedAt() != null ? CONTROLLED : TO_COUNT_STATUS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockItemResponse> getStock(UUID categoryId) {
        Map<UUID, BigDecimal> incoming = new HashMap<>();
        for (Object[] row : purchaseRepository.sumIncoming(PurchaseStatus.ANULADO, DeliveryStatus.PENDIENTE)) {
            incoming.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return productRepository.findByStatusNotOrderByNameAsc(ProductStatus.ELIMINADO).stream()
            .filter(p -> !p.isSpecial())
            .filter(p -> categoryId == null || (p.getCategory() != null && categoryId.equals(p.getCategory().getId())))
            .map(p -> stockItem(p, incoming.get(p.getId())))
            .toList();
    }

    private static StockItemResponse stockItem(Product p, BigDecimal incoming) {
        String status = statusOf(p);
        boolean controlled = CONTROLLED.equals(status);
        BigDecimal quantity = controlled ? Objects.requireNonNullElse(p.getStockQuantity(), ZERO_QTY) : null;
        return new StockItemResponse(p.getId(), p.getCode(), p.getName(),
            p.getCategory() != null ? p.getCategory().getId() : null,
            p.getCategory() != null ? p.getCategory().getName() : null,
            p.getSupplier() != null ? p.getSupplier().getName() : null,
            p.getSaleUnit(), status, controlled && quantity.signum() < 0, quantity,
            controlled ? p.getStockAverageCost() : null,
            controlled ? Objects.requireNonNullElse(p.getStockValue(), ZERO_MONEY) : null,
            incoming != null ? incoming : ZERO_QTY,
            packagesOf(p));
    }

    static List<StockItemResponse.PackageItem> packagesOf(Product p) {
        return p.getPresentations().stream().filter(ProductPresentation::isActive)
            .map(pp -> new StockItemResponse.PackageItem(pp.getName(), pp.getBaseQuantity()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLevelResponse> getLevels() {
        return productStockRepository.findAll().stream()
            .filter(ProductStock::isStarted)
            .map(s -> new StockLevelResponse(s.getProductId(), s.getQuantity()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KardexResponse getKardex(UUID productId, LocalDate from, LocalDate to) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        LocalDate today = StoreFormat.today(clock);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(KARDEX_RANGE);
        }
        Instant fromInstant = start.atStartOfDay(StoreFormat.STORE_ZONE).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(StoreFormat.STORE_ZONE).toInstant();
        Optional<StockMovement> opening = stockMovementRepository
            .findFirstByProductIdAndCreatedAtBeforeOrderBySeqDesc(productId, fromInstant);
        List<KardexResponse.Row> rows = stockMovementRepository.findInRange(productId, fromInstant, toInstant).stream()
            .map(InventoryServiceImpl::row)
            .toList();
        ProductStock stock = productStockRepository.findById(productId).orElse(null);
        return new KardexResponse(product.getId(), product.getCode(), product.getName(), product.getSaleUnit(),
            statusOf(product), start, end,
            opening.map(StockMovement::getBalanceQuantity).orElse(ZERO_QTY),
            opening.map(StockMovement::getBalanceValue).orElse(ZERO_MONEY),
            opening.map(StockMovement::getBalanceAverageCost).orElse(null),
            rows,
            stock != null ? stock.getQuantity() : ZERO_QTY,
            stock != null ? stock.getValue() : ZERO_MONEY,
            stock != null ? stock.getAverageCost() : null,
            packagesOf(product));
    }

    private static KardexResponse.Row row(StockMovement m) {
        return new KardexResponse.Row(m.getId(), m.getCreatedAt(), m.getType().name(), m.getType().label(),
            m.getReference(), m.getReason() != null ? m.getReason().label() : null, m.getComment(), m.getQuantity(),
            m.getUnitCost(), m.getValue(), m.getExpensedValue(), m.getBalanceQuantity(), m.getBalanceValue(),
            m.getBalanceAverageCost(), m.getCreatedBy());
    }

    @Override
    public KardexResponse adjust(UUID productId, StockAdjustmentRequest request, String username) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        if (!product.isStockControlled() || product.isSpecial()) {
            throw new IllegalArgumentException(String.format(NOT_CONTROLLED, product.getName()));
        }
        if (!product.controlsStockNow()) {
            throw new IllegalArgumentException(String.format(TO_COUNT, product.getName()));
        }
        if (request.reason() == null) {
            throw new IllegalArgumentException("Elige el motivo del ajuste.");
        }
        BigDecimal quantity = request.quantity() != null
            ? SaleServiceImpl.normalizeQuantity(request.quantity(), product.getName(), product.getSaleUnit())
            : null;
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(ADJUSTMENT_QUANTITY);
        }
        ProductStock stock = ledger.lock(List.of(productId)).get(productId);
        BigDecimal cost = averageCostOf(stock, product);
        String comment = request.comment() != null && !request.comment().isBlank() ? request.comment().trim() : null;
        InventoryLedger.Source source = InventoryLedger.Source.builder().reason(request.reason()).comment(comment)
            .username(username).build();
        if (request.reason().adds()) {
            ledger.entry(stock, StockMovementType.ADJUSTMENT, quantity, quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP), source);
        } else {
            ledger.exitAt(stock, StockMovementType.ADJUSTMENT, quantity, cost, source);
        }
        return getKardex(productId, null, null);
    }

    /** INV-06: the average of the stock, to the cent; without one yet, the last cost. */
    static BigDecimal averageCostOf(ProductStock stock, Product product) {
        if (stock.getAverageCost() != null) {
            return stock.getAverageCost().setScale(2, RoundingMode.HALF_UP);
        }
        return product.getBuyPrice() != null ? product.getBuyPrice().setScale(2, RoundingMode.HALF_UP) : ZERO_MONEY;
    }
}
