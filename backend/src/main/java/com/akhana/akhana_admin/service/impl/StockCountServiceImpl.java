package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.StockCountLineRequest;
import com.akhana.akhana_admin.dto.StockCountRequest;
import com.akhana.akhana_admin.dto.StockCountResponse;
import com.akhana.akhana_admin.dto.StockCountReviewResponse;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.StockCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * INV-05, INV-18: counts by category or product, blind, in packages and loose units. What is expected is the stock
 * when each quantity is written; confirming generates the adjustments, or the initial load of a product still to count.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockCountServiceImpl implements StockCountService {

    static final String COUNT_CLOSED = "El conteo ya está cerrado.";
    static final String NOT_COUNTABLE = "'%s' no controla stock: no se cuenta.";
    static final String IN_OTHER_COUNT = "'%s' ya está en el Conteo #%d, que sigue abierto.";
    static final String PACKAGE_NOT_FOUND = "El empaque elegido no es de '%s' o ya no está activo.";
    static final String WHOLE_PACKAGES = "La cantidad de '%s' debe ser un número entero de 0 o más.";
    static final String LOOSE_NEGATIVE = "Las sueltas de '%s' no pueden ser negativas.";
    static final String NOTHING_COUNTED = "El conteo no tiene ningún producto contado.";

    private static final BigDecimal ZERO_QTY = BigDecimal.ZERO.setScale(3);
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);

    private final StockCountRepository stockCountRepository;
    private final StockCountLineRepository stockCountLineRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final SaleItemRepository saleItemRepository;
    private final ConsumptionRepository consumptionRepository;
    private final InventoryLedger ledger;
    private final Clock clock;

    @Override
    public StockCountResponse create(StockCountRequest request, String username) {
        Category category = null;
        if (request != null && request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + request.categoryId()));
        }
        StockCount count = stockCountRepository.save(StockCount.builder()
            .countNumber(stockCountRepository.nextCountNumber())
            .category(category)
            .status(StockCountStatus.ABIERTO)
            .createdBy(username)
            .createdAt(Instant.now(clock))
            .build());
        return response(count);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockCountResponse> list() {
        return stockCountRepository.findTop50ByOrderByCreatedAtDesc().stream()
            .map(c -> summary(c))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockCountResponse get(UUID countId) {
        return response(find(countId));
    }

    @Override
    public StockCountResponse saveLine(UUID countId, UUID productId, StockCountLineRequest request, String username) {
        StockCount count = stockCountRepository.findByIdForUpdate(countId)
            .orElseThrow(() -> new ResourceNotFoundException("Conteo no encontrado con ID: " + countId));
        requireOpen(count);
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        if (!countable(product)) {
            throw new IllegalArgumentException(String.format(NOT_COUNTABLE, product.getName()));
        }
        for (StockCount other : stockCountRepository.findByStatusOrderByCreatedAtDesc(StockCountStatus.ABIERTO)) {
            if (!other.getId().equals(countId)
                    && other.getLines().stream().anyMatch(l -> l.getProduct().getId().equals(productId))) {
                throw new IllegalArgumentException(String.format(IN_OTHER_COUNT, product.getName(), other.getCountNumber()));
            }
        }
        Counted counted = counted(product, request);
        // INV-05: what is expected is the stock right now, when the quantity is written; a product still to count
        // (INV-18) expects nothing: this count is its initial load
        ProductStock stock = productStockRepository.findById(productId).orElse(null);
        boolean initialLoad = stock == null || !stock.isStarted();
        BigDecimal expected = initialLoad ? ZERO_QTY : stock.getQuantity();
        Instant now = Instant.now(clock);
        StockCountLine line = stockCountLineRepository.findByStockCountIdAndProductId(countId, productId)
            .orElseGet(() -> {
                StockCountLine created = StockCountLine.builder().stockCount(count).product(product).build();
                count.getLines().add(created);
                return created;
            });
        line.setCountedQuantity(counted.quantity());
        line.setBreakdown(counted.breakdown());
        line.setExpectedQuantity(expected);
        line.setInitialLoad(initialLoad);
        line.setCountedBy(username);
        line.setCountedAt(now);
        stockCountLineRepository.save(line);
        return response(count);
    }

    @Override
    @Transactional(readOnly = true)
    public StockCountReviewResponse review(UUID countId) {
        StockCount count = find(countId);
        List<StockCountReviewResponse.Line> lines = new ArrayList<>();
        BigDecimal total = ZERO_MONEY;
        for (StockCountLine line : count.getLines()) {
            Plan plan = plan(line);
            lines.add(new StockCountReviewResponse.Line(line.getProduct().getId(), line.getProduct().getCode(),
                line.getProduct().getName(), line.getProduct().getSaleUnit(), line.isInitialLoad(),
                line.getCountedQuantity(), line.getBreakdown(), plan.expected(), plan.difference(), plan.unitCost(),
                plan.value()));
            total = total.add(plan.value());
        }
        int notCounted = (int) productsToCount(count).stream()
            .filter(p -> count.getLines().stream().noneMatch(l -> l.getProduct().getId().equals(p.getId())))
            .count();
        return new StockCountReviewResponse(count.getId(), count.getCountNumber(),
            count.getCategory() != null ? count.getCategory().getName() : null, lines, notCounted, total);
    }

    @Override
    public StockCountResponse confirm(UUID countId, String username) {
        StockCount count = stockCountRepository.findByIdForUpdate(countId)
            .orElseThrow(() -> new ResourceNotFoundException("Conteo no encontrado con ID: " + countId));
        requireOpen(count);
        if (count.getLines().isEmpty()) {
            throw new IllegalArgumentException(NOTHING_COUNTED);
        }
        // T-14: the count, then the stock of its products, together and by id
        Map<UUID, ProductStock> stocks = ledger.lock(count.getLines().stream().map(l -> l.getProduct().getId()).toList());
        String reference = "Conteo #" + count.getCountNumber();
        for (StockCountLine line : count.getLines()) {
            Product product = line.getProduct();
            if (!countable(product)) {
                // It stopped controlling its stock after it was counted (CAT-05)
                continue;
            }
            ProductStock stock = stocks.get(product.getId());
            InventoryLedger.Source source = InventoryLedger.Source.builder().reference(reference)
                .stockCountLineId(line.getId()).comment(line.getBreakdown()).username(username).build();
            if (line.isInitialLoad()) {
                // INV-18: what was counted at that day's cost, less what left after it was written
                BigDecimal load = line.getCountedQuantity().subtract(soldSince(product.getId(), line.getCountedAt()));
                ledger.initialLoad(stock, load, lastCost(product), source);
                continue;
            }
            BigDecimal difference = line.getCountedQuantity().subtract(line.getExpectedQuantity());
            BigDecimal cost = InventoryServiceImpl.averageCostOf(stock, product);
            if (difference.signum() > 0) {
                ledger.entry(stock, StockMovementType.COUNT_ADJUSTMENT, difference,
                    difference.multiply(cost).setScale(2, RoundingMode.HALF_UP), source);
            } else if (difference.signum() < 0) {
                ledger.exitAt(stock, StockMovementType.COUNT_ADJUSTMENT, difference.negate(), cost, source);
            }
        }
        count.setStatus(StockCountStatus.CONFIRMADO);
        count.setClosedBy(username);
        count.setClosedAt(Instant.now(clock));
        stockCountRepository.save(count);
        return response(count);
    }

    @Override
    public StockCountResponse discard(UUID countId, String username) {
        StockCount count = stockCountRepository.findByIdForUpdate(countId)
            .orElseThrow(() -> new ResourceNotFoundException("Conteo no encontrado con ID: " + countId));
        requireOpen(count);
        count.setStatus(StockCountStatus.DESCARTADO);
        count.setClosedBy(username);
        count.setClosedAt(Instant.now(clock));
        stockCountRepository.save(count);
        return response(count);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private record Counted(BigDecimal quantity, String breakdown) {
    }

    private record Plan(BigDecimal expected, BigDecimal difference, BigDecimal unitCost, BigDecimal value) {
    }

    private Plan plan(StockCountLine line) {
        Product product = line.getProduct();
        if (line.isInitialLoad()) {
            BigDecimal cost = lastCost(product);
            BigDecimal load = line.getCountedQuantity().subtract(soldSince(product.getId(), line.getCountedAt()));
            return new Plan(ZERO_QTY, load, cost, load.multiply(cost).setScale(2, RoundingMode.HALF_UP));
        }
        ProductStock stock = productStockRepository.findById(product.getId()).orElseThrow();
        BigDecimal cost = InventoryServiceImpl.averageCostOf(stock, product);
        BigDecimal difference = line.getCountedQuantity().subtract(line.getExpectedQuantity());
        return new Plan(line.getExpectedQuantity(), difference, cost,
            difference.multiply(cost).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal soldSince(UUID productId, Instant since) {
        return saleItemRepository.sumSoldSince(productId, since).add(consumptionRepository.sumConsumedSince(productId, since));
    }

    private static BigDecimal lastCost(Product product) {
        return product.getBuyPrice() != null ? product.getBuyPrice().setScale(2, RoundingMode.HALF_UP) : ZERO_MONEY;
    }

    private static boolean countable(Product product) {
        return !product.isSpecial() && product.isStockControlled() && product.getStatus() != ProductStatus.ELIMINADO;
    }

    // «3 Caja ×12 + 4», «2 Saco 25 kg + 3,250 kg», «0»
    private Counted counted(Product product, StockCountLineRequest request) {
        BigDecimal total = ZERO_QTY;
        List<String> parts = new ArrayList<>();
        if (request != null && request.packages() != null) {
            for (StockCountLineRequest.PackageCount pc : request.packages()) {
                if (pc == null || pc.quantity() == null || pc.quantity().signum() == 0) {
                    continue;
                }
                ProductPresentation presentation = product.getPresentations().stream()
                    .filter(p -> p.isActive() && p.getId().equals(pc.presentationId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(String.format(PACKAGE_NOT_FOUND, product.getName())));
                if (pc.quantity().signum() < 0 || pc.quantity().stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException(String.format(WHOLE_PACKAGES,
                        product.getName() + " · " + presentation.getName()));
                }
                total = total.add(pc.quantity().multiply(presentation.getBaseQuantity()));
                parts.add(pc.quantity().setScale(0, RoundingMode.HALF_UP).toPlainString() + " " + presentation.getName());
            }
        }
        BigDecimal loose = request != null && request.loose() != null ? request.loose() : BigDecimal.ZERO;
        if (loose.signum() < 0) {
            throw new IllegalArgumentException(String.format(LOOSE_NEGATIVE, product.getName()));
        }
        if (loose.signum() > 0) {
            loose = SaleServiceImpl.normalizeQuantity(loose, product.getName(), product.getSaleUnit());
            total = total.add(loose);
            parts.add(InventoryServiceImpl.quantityText(loose, product.getSaleUnit()));
        }
        return new Counted(total.setScale(3, RoundingMode.HALF_UP), parts.isEmpty() ? "0" : String.join(" + ", parts));
    }

    private StockCount find(UUID countId) {
        return stockCountRepository.findById(countId)
            .orElseThrow(() -> new ResourceNotFoundException("Conteo no encontrado con ID: " + countId));
    }

    private static void requireOpen(StockCount count) {
        if (count.getStatus() != StockCountStatus.ABIERTO) {
            throw new IllegalStateException(COUNT_CLOSED);
        }
    }

    private List<Product> productsToCount(StockCount count) {
        if (count.getCategory() == null) {
            return List.of();
        }
        UUID categoryId = count.getCategory().getId();
        return productRepository.findByStatusNotOrderByNameAsc(ProductStatus.ELIMINADO).stream()
            .filter(StockCountServiceImpl::countable)
            .filter(p -> p.getCategory() != null && categoryId.equals(p.getCategory().getId()))
            .toList();
    }

    private StockCountResponse summary(StockCount count) {
        return new StockCountResponse(count.getId(), count.getCountNumber(),
            count.getCategory() != null ? count.getCategory().getId() : null,
            count.getCategory() != null ? count.getCategory().getName() : null, count.getStatus(),
            count.getCreatedBy(), count.getCreatedAt(), count.getClosedBy(), count.getClosedAt(),
            count.getLines().size(), List.of());
    }

    // Blind (INV-05): the products to count and what was counted, never their stock
    private StockCountResponse response(StockCount count) {
        Map<UUID, StockCountLine> lines = new LinkedHashMap<>();
        count.getLines().forEach(l -> lines.put(l.getProduct().getId(), l));
        List<Product> products = new ArrayList<>(productsToCount(count));
        Set<UUID> listed = new HashSet<>();
        products.forEach(p -> listed.add(p.getId()));
        count.getLines().stream().map(StockCountLine::getProduct).filter(p -> listed.add(p.getId())).forEach(products::add);
        List<StockCountResponse.CountProduct> items = products.stream().map(p -> {
            StockCountLine line = lines.get(p.getId());
            List<StockCountResponse.CountPackage> packages = p.getPresentations().stream()
                .filter(ProductPresentation::isActive)
                .map(pp -> new StockCountResponse.CountPackage(pp.getId(), pp.getName(), pp.getBaseQuantity(),
                    p.getBarcodes().stream().filter(b -> b.isActive() && pp.getId().equals(b.getPresentationId()))
                        .map(ProductBarcode::getBarcode).findFirst().orElse(null)))
                .toList();
            List<String> barcodes = p.getBarcodes().stream().filter(b -> b.isActive() && b.getPresentationId() == null)
                .map(ProductBarcode::getBarcode).toList();
            return new StockCountResponse.CountProduct(p.getId(), p.getCode(), p.getName(), p.getSaleUnit(),
                p.getStockStartedAt() != null ? InventoryServiceImpl.CONTROLLED : InventoryServiceImpl.TO_COUNT_STATUS,
                barcodes, packages,
                line != null ? line.getCountedQuantity() : null, line != null ? line.getBreakdown() : null,
                line != null ? line.getCountedBy() : null, line != null ? line.getCountedAt() : null);
        }).toList();
        return new StockCountResponse(count.getId(), count.getCountNumber(),
            count.getCategory() != null ? count.getCategory().getId() : null,
            count.getCategory() != null ? count.getCategory().getName() : null, count.getStatus(),
            count.getCreatedBy(), count.getCreatedAt(), count.getClosedBy(), count.getClosedAt(),
            count.getLines().size(), items);
    }
}
