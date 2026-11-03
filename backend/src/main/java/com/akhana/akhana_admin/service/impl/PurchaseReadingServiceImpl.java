package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.PurchaseDraftResponse;
import com.akhana.akhana_admin.dto.PurchaseReadingStatusResponse;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.PurchaseReadingRepository;
import com.akhana.akhana_admin.repository.PurchaseRepository;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.service.PurchaseReader;
import com.akhana.akhana_admin.service.PurchaseReader.ReadLine;
import com.akhana.akhana_admin.service.PurchaseReader.ReadOutcome;
import com.akhana.akhana_admin.service.PurchaseReader.ReadPurchase;
import com.akhana.akhana_admin.service.PurchaseReadingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

// COM-20: not transactional, since the call to the AI can take most of a minute; each save commits on its own, so a
// failed reading still counts for the cap (COM-25). Nothing here touches a purchase: a person registers it afterwards.
@Service
public class PurchaseReadingServiceImpl implements PurchaseReadingService {

    private static final BigDecimal TOKENS_PER_PRICE = BigDecimal.valueOf(1_000_000);
    // A call that does not report its usage may still be charged: about 2.5 times what an average photo costs
    static final BigDecimal UNKNOWN_CALL_COST_USD = new BigDecimal("0.010000");
    static final int MAX_SUGGESTIONS = 5;
    static final String NO_COST_NOTE = "No se pudo saber el costo de esta línea: llénalo para registrar.";
    // A call holds a request thread, and a database connection with open-in-view, which the POS shares
    static final int MAX_READINGS_IN_PROCESS = 3;

    private final PurchaseReader reader;
    private final PurchaseReadingRepository readingRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final ProductPhotoProcessor photoProcessor;
    private final BigDecimal monthlyCapUsd;
    private final BigDecimal inputPricePerMillion;
    private final BigDecimal outputPricePerMillion;
    private final Semaphore permits = new Semaphore(MAX_READINGS_IN_PROCESS);

    public PurchaseReadingServiceImpl(PurchaseReader reader,
                                      PurchaseReadingRepository readingRepository,
                                      PurchaseRepository purchaseRepository,
                                      ProductRepository productRepository,
                                      SupplierRepository supplierRepository,
                                      ProductPhotoProcessor photoProcessor,
                                      @Value("${app.openai.monthly-cap-usd:10.00}") BigDecimal monthlyCapUsd,
                                      @Value("${app.openai.input-price-per-million:1.00}") BigDecimal inputPricePerMillion,
                                      @Value("${app.openai.output-price-per-million:5.00}") BigDecimal outputPricePerMillion) {
        this.reader = reader;
        this.readingRepository = readingRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.photoProcessor = photoProcessor;
        this.monthlyCapUsd = monthlyCapUsd;
        this.inputPricePerMillion = inputPricePerMillion;
        this.outputPricePerMillion = outputPricePerMillion;
    }

    @Override
    public PurchaseReadingStatusResponse getStatus(boolean includeSpend) {
        BigDecimal spent = monthSpend();
        return new PurchaseReadingStatusResponse(reader.isConfigured(), spent.compareTo(monthlyCapUsd) >= 0,
            includeSpend ? spent.setScale(2, RoundingMode.HALF_UP) : null,
            includeSpend ? monthlyCapUsd.setScale(2, RoundingMode.HALF_UP) : null);
    }

    @Override
    public PurchaseDraftResponse readText(String text, UUID supplierId, String username) {
        // A wrong supplier is rejected before spending on the call
        com.akhana.akhana_admin.model.Supplier chosen = supplierId != null ? activeSupplier(supplierId) : null;
        ensureCanRead();
        Saved saved = call(PurchaseReadingSource.TEXT, () -> reader.readText(text), username);
        if (saved.purchase().multipleSuppliers()) {
            throw new IllegalArgumentException(ONE_SUPPLIER);
        }
        return draft(saved, chosen);
    }

    @Override
    public PurchaseDraftResponse readPhoto(byte[] content, String username) {
        // The checks of a product photo (CAT-12) run first: a rejected file never reaches the AI (CA-23)
        ensureCanRead();
        byte[] reduced = photoProcessor.process(content).image();
        Saved saved = call(PurchaseReadingSource.PHOTO, () -> reader.readPhoto(reduced), username);
        return draft(saved, null);
    }

    private record Saved(PurchaseReading reading, ReadPurchase purchase) {}

    private void ensureCanRead() {
        if (!reader.isConfigured()) {
            throw new IllegalStateException(NOT_CONFIGURED);
        }
        if (monthSpend().compareTo(monthlyCapUsd) >= 0) {
            throw new IllegalStateException(CAP_REACHED);
        }
    }

    private Saved call(PurchaseReadingSource source, Supplier<ReadOutcome> read, String username) {
        if (!permits.tryAcquire()) {
            throw new IllegalStateException(READ_FAILED);
        }
        ReadOutcome outcome;
        try {
            outcome = read.get();
        } finally {
            permits.release();
        }
        boolean failed = outcome.purchase() == null || outcome.purchase().lines().isEmpty();
        PurchaseReading reading = readingRepository.save(PurchaseReading.builder()
            .source(source)
            .status(failed ? PurchaseReadingStatus.FAILED : PurchaseReadingStatus.READ)
            .model(reader.model())
            .result(outcome.rawResult())
            .inputTokens(outcome.inputTokens())
            .outputTokens(outcome.outputTokens())
            .costUsd(cost(outcome))
            .createdBy(username)
            .createdAt(Instant.now())
            .build());
        if (failed) {
            throw new IllegalStateException(READ_FAILED);
        }
        return new Saved(reading, outcome.purchase());
    }

    // Rounded up: what is counted is never below what was charged
    private BigDecimal cost(ReadOutcome outcome) {
        if (outcome.inputTokens() == null || outcome.outputTokens() == null) {
            return UNKNOWN_CALL_COST_USD;
        }
        return BigDecimal.valueOf(outcome.inputTokens()).multiply(inputPricePerMillion)
            .add(BigDecimal.valueOf(outcome.outputTokens()).multiply(outputPricePerMillion))
            .divide(TOKENS_PER_PRICE, 6, RoundingMode.CEILING);
    }

    private BigDecimal monthSpend() {
        YearMonth month = YearMonth.now(StoreFormat.STORE_ZONE);
        return readingRepository.sumCostBetween(
            month.atDay(1).atStartOfDay(StoreFormat.STORE_ZONE).toInstant(),
            month.plusMonths(1).atDay(1).atStartOfDay(StoreFormat.STORE_ZONE).toInstant());
    }

    private com.akhana.akhana_admin.model.Supplier activeSupplier(UUID supplierId) {
        com.akhana.akhana_admin.model.Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + supplierId));
        if (supplier.getStatus() != SupplierStatus.ACTIVO) {
            throw new IllegalArgumentException("El proveedor '" + supplier.getName() + "' no está activo.");
        }
        return supplier;
    }

    private PurchaseDraftResponse draft(Saved saved, com.akhana.akhana_admin.model.Supplier chosen) {
        ReadPurchase read = saved.purchase();
        PurchaseReadingSource source = saved.reading().getSource();
        // What the person chose stands over what the message seems to say
        com.akhana.akhana_admin.model.Supplier supplier = chosen != null ? chosen : matchSupplier(read.supplier());
        UUID supplierId = supplier != null ? supplier.getId() : null;
        List<Product> catalog = productRepository.findByStatusNotOrderByNameAsc(ProductStatus.ELIMINADO).stream()
            .filter(product -> !product.isSpecial())
            .toList();

        List<PurchaseDraftResponse.Line> lines = read.lines().stream()
            .map(line -> draftLine(line, source, supplierId, catalog))
            .toList();
        boolean photo = source == PurchaseReadingSource.PHOTO;
        return new PurchaseDraftResponse(
            saved.reading().getId(),
            source,
            supplierId,
            read.supplier(),
            read.looseNote(),
            photo && read.paidNote(),
            lines,
            photo ? money(read.shipping()) : null,
            photo ? money(read.total()) : null,
            photo ? pendingPurchases(supplierId) : List.of());
    }

    private PurchaseDraftResponse.Line draftLine(ReadLine line, PurchaseReadingSource source, UUID supplierId, List<Product> catalog) {
        String text = line.product() != null ? line.product() : "";
        boolean validQuantity = line.quantity() != null && line.quantity().signum() > 0;
        BigDecimal quantity = validQuantity ? line.quantity().stripTrailingZeros() : BigDecimal.ONE;
        if (quantity.scale() < 0) {
            quantity = quantity.setScale(0, RoundingMode.UNNECESSARY);
        }

        List<ProductTextMatcher.Match> ranked = rankForSupplier(ProductTextMatcher.search(text, catalog), supplierId);
        Product product = chosenProduct(ranked, supplierId);
        // Missing words only ever suggest: a person chooses (COM-21)
        List<ProductTextMatcher.Match> candidates = !ranked.isEmpty() ? ranked
            : rankForSupplier(ProductTextMatcher.searchPartial(text, catalog), supplierId);
        List<PurchaseDraftResponse.ProductSuggestion> suggestions = product != null ? List.of() : candidates.stream()
            .limit(MAX_SUGGESTIONS)
            .map(match -> new PurchaseDraftResponse.ProductSuggestion(match.product().getId(), match.product().getCode(),
                match.product().getName(), match.product().getSupplier() != null ? match.product().getSupplier().getName() : null))
            .toList();

        BigDecimal unitCost;
        String costNote = null;
        if (source == PurchaseReadingSource.TEXT) {
            // COM-22: a message does not carry costs; the system's cost stands until the real one is registered
            unitCost = product != null ? product.getBuyPrice().setScale(2, RoundingMode.HALF_UP) : null;
        } else if (line.unitCost() != null && line.unitCost().signum() >= 0) {
            unitCost = line.unitCost().setScale(2, RoundingMode.HALF_UP);
        } else if (line.lineAmount() != null && line.lineAmount().signum() >= 0 && validQuantity) {
            unitCost = line.lineAmount().divide(quantity, 2, RoundingMode.HALF_UP);
            costNote = "Calculado: importe " + StoreFormat.money(line.lineAmount()) + " ÷ " + quantity.toPlainString() + ".";
        } else {
            unitCost = null;
            costNote = line.note() != null ? line.note() : NO_COST_NOTE;
        }

        return new PurchaseDraftResponse.Line(text, quantity, line.quantityDoubtful() || !validQuantity, line.quantityText(),
            product != null ? product.getId() : null, suggestions, unitCost, costNote);
    }

    // The supplier's own products first, keeping the order of the search
    private static List<ProductTextMatcher.Match> rankForSupplier(List<ProductTextMatcher.Match> ranked, UUID supplierId) {
        if (supplierId == null) {
            return ranked;
        }
        List<ProductTextMatcher.Match> own = new ArrayList<>();
        List<ProductTextMatcher.Match> others = new ArrayList<>();
        ranked.forEach(match -> (isFrom(match.product(), supplierId) ? own : others).add(match));
        own.addAll(others);
        return own;
    }

    // Only when it is clear which one (CA-02): the only match or the exact name, among the supplier's products if it is
    // known. A product of another supplier, a typo or a size that is not there are suggestions: a person chooses
    private static Product chosenProduct(List<ProductTextMatcher.Match> ranked, UUID supplierId) {
        List<ProductTextMatcher.Match> eligible = ranked.stream()
            .filter(ProductTextMatcher.Match::clear)
            .filter(match -> supplierId == null || isFrom(match.product(), supplierId))
            .toList();
        if (eligible.size() == 1) {
            return eligible.get(0).product();
        }
        List<ProductTextMatcher.Match> exact = eligible.stream()
            .filter(match -> match.score() >= ProductTextMatcher.EXACT_SCORE)
            .toList();
        return exact.size() == 1 ? exact.get(0).product() : null;
    }

    private static boolean isFrom(Product product, UUID supplierId) {
        return product.getSupplier() != null && supplierId.equals(product.getSupplier().getId());
    }

    // CA-07: with the active suppliers, and only when a single one fits
    private com.akhana.akhana_admin.model.Supplier matchSupplier(String text) {
        String term = ProductTextMatcher.normalize(text);
        if (term.isEmpty()) {
            return null;
        }
        List<com.akhana.akhana_admin.model.Supplier> active = supplierRepository.findByStatusOrderByNameAsc(SupplierStatus.ACTIVO);
        List<com.akhana.akhana_admin.model.Supplier> exact = active.stream()
            .filter(supplier -> ProductTextMatcher.normalize(supplier.getName()).equals(term))
            .toList();
        if (exact.size() == 1) {
            return exact.get(0);
        }
        List<com.akhana.akhana_admin.model.Supplier> containing = active.stream()
            .filter(supplier -> {
                String name = ProductTextMatcher.normalize(supplier.getName());
                return name.contains(term) || term.contains(name);
            })
            .toList();
        return containing.size() == 1 ? containing.get(0) : null;
    }

    private List<PurchaseDraftResponse.PendingPurchase> pendingPurchases(UUID supplierId) {
        if (supplierId == null) {
            return List.of();
        }
        return purchaseRepository.findPendingDelivery(supplierId).stream()
            .map(PurchaseDraftResponse.PendingPurchase::from)
            .toList();
    }

    private static BigDecimal money(BigDecimal value) {
        return value != null && value.signum() >= 0 ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }
}
