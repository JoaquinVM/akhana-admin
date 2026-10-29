package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseServiceImpl implements PurchaseService {

    static final ZoneId STORE_ZONE = ZoneId.of("America/La_Paz");

    static final String NO_ITEMS = "El pedido debe tener al menos una línea.";
    static final String FUTURE_DATE = "La fecha del pedido no puede ser futura.";
    static final String SHIPPING_ACCOUNT_REQUIRED = "Indique la cuenta con la que se pagó el envío.";
    static final String SHIPPING_CATEGORY_MISSING = "No existe la categoría Transporte; no se puede registrar el envío.";
    static final String PAYMENT_CATEGORY_MISSING = "No existe la categoría Pedido; no se puede registrar el pago.";
    static final String CASH_SESSION_REQUIRED = "Los movimientos de la cuenta Caja requieren una caja abierta.";
    static final String VOIDED_PURCHASE = "El pedido está anulado.";
    static final String ALREADY_VOIDED_PURCHASE = "El pedido ya está anulado.";
    static final String ALREADY_VOIDED_PAYMENT = "El pago ya está anulado.";
    static final String VOID_REASON_REQUIRED = "El motivo de anulación es obligatorio.";
    static final String CLOSED_SESSION_PAYMENT = "El pago se hizo en una caja cerrada; no se puede anular.";
    static final String CLOSED_SESSION_PURCHASE = "El pedido tiene movimientos de una caja cerrada; no se puede anular.";
    static final String FUTURE_DELIVERY_DATE = "La fecha de entrega no puede ser futura.";
    static final String DELIVERY_BEFORE_PURCHASE = "La fecha de entrega no puede ser anterior a la fecha del pedido.";
    static final String PAYMENT_SOURCE_REQUIRED = "Elija una cuenta o una persona como origen del pago.";

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductPriceChangeRepository productPriceChangeRepository;
    private final CashSessionRepository cashSessionRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ExpectedCashCalculator expectedCashCalculator;
    private final CreditorRepository creditorRepository;

    /** Product cost and price change confirmed on a purchase line (COM-03), applied after the purchase is saved. */
    private record CostUpdate(Product product, BigDecimal newCost, BigDecimal newSellPrice) {}

    /** Locked cash session (only for Caja) and account that pay a movement. */
    private record Payer(FinancialAccount account, CashSession session) {}

    /** Validated delivery of a purchase (COM-07). */
    private record Delivery(DeliveryType type, DeliveryStatus status, LocalDate date) {}

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseResponse> getPurchases(UUID supplierId, PurchaseStatus status, PurchasePaymentStatus paymentStatus,
                                               DeliveryStatus deliveryStatus, String search) {
        String term = search != null && !search.isBlank() ? search.trim() : null;
        return purchaseRepository.search(supplierId, term).stream()
            .filter(p -> status == null || p.getStatus() == status)
            // A voided purchase has no payment status to filter by
            .filter(p -> paymentStatus == null
                || (p.getStatus() == PurchaseStatus.REGISTRADO && p.getPaymentStatus() == paymentStatus))
            .filter(p -> deliveryStatus == null || p.getDeliveryStatus() == deliveryStatus)
            .map(PurchaseResponse::summary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayablesResponse getPayables(UUID supplierId) {
        Map<UUID, List<Purchase>> bySupplier = new LinkedHashMap<>();
        for (Purchase purchase : purchaseRepository.findPayables(supplierId)) {
            bySupplier.computeIfAbsent(purchase.getSupplier().getId(), id -> new ArrayList<>()).add(purchase);
        }
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<PayablesResponse.SupplierPayables> suppliers = new ArrayList<>();
        for (List<Purchase> purchases : bySupplier.values()) {
            BigDecimal balance = purchases.stream()
                .map(Purchase::getBalance)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            Supplier supplier = purchases.get(0).getSupplier();
            suppliers.add(new PayablesResponse.SupplierPayables(supplier.getId(), supplier.getName(), balance,
                purchases.stream().map(PurchaseResponse::summary).toList()));
            total = total.add(balance);
        }
        return new PayablesResponse(total, suppliers);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseResponse getPurchaseById(UUID id) {
        Purchase purchase = purchaseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + id));
        Set<UUID> paymentTxIds = new HashSet<>();
        purchase.getPayments().forEach(p -> paymentTxIds.add(p.getFinancialTransactionId()));
        // The shipping expense is the purchase movement that no payment points to (P-24)
        String shippingAccountName = financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(id).stream()
            .filter(tx -> !paymentTxIds.contains(tx.getId()))
            .map(tx -> tx.getSourceAccount().getName())
            .findFirst()
            .orElse(null);
        return PurchaseResponse.detail(purchase, shippingAccountName);
    }

    @Override
    public PurchaseResponse registerPurchase(PurchaseRequest request, String username) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException(NO_ITEMS);
        }
        Supplier supplier = supplierRepository.findById(request.supplierId())
            .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + request.supplierId()));
        if (supplier.getStatus() != SupplierStatus.ACTIVO) {
            throw new IllegalArgumentException("El proveedor '" + supplier.getName() + "' no está activo.");
        }

        LocalDate today = LocalDate.now(STORE_ZONE);
        LocalDate purchaseDate = request.purchaseDate() != null ? request.purchaseDate() : today;
        if (purchaseDate.isAfter(today)) {
            throw new IllegalArgumentException(FUTURE_DATE);
        }
        Delivery delivery = resolveDelivery(request.deliveryType(), request.deliveryStatus(), request.deliveryDate(),
            purchaseDate, today);

        List<PurchaseItem> items = new ArrayList<>();
        List<CostUpdate> costUpdates = new ArrayList<>();
        Set<UUID> updatedProducts = new HashSet<>();
        BigDecimal itemsTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (PurchaseItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + itemReq.productId()));
            if (product.getStatus() == ProductStatus.ELIMINADO) {
                throw new IllegalArgumentException("El producto '" + product.getName() + "' está eliminado.");
            }
            ProductServiceImpl.ensureNotSpecial(product);

            BigDecimal quantity = SaleServiceImpl.normalizeQuantity(itemReq.quantity(), product.getName(), product.getSaleUnit());
            if (itemReq.unitCost() == null || itemReq.unitCost().signum() < 0) {
                throw new IllegalArgumentException("El costo unitario de '" + product.getName() + "' no puede ser negativo.");
            }
            BigDecimal unitCost = itemReq.unitCost().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = unitCost.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            BigDecimal currentCost = product.getBuyPrice().setScale(2, RoundingMode.HALF_UP);

            boolean costChanges = Boolean.TRUE.equals(itemReq.updateProductCost()) && unitCost.compareTo(currentCost) != 0;
            if (costChanges) {
                costUpdates.add(planCostUpdate(product, unitCost, itemReq.newSellPrice(), updatedProducts));
            }

            items.add(PurchaseItem.builder()
                .product(product)
                .productName(product.getName())
                .productCode(product.getCode())
                .quantity(quantity)
                .unitCost(unitCost)
                .subtotal(subtotal)
                .previousUnitCost(currentCost)
                .costUpdated(costChanges)
                .build());
            itemsTotal = itemsTotal.add(subtotal);
        }

        BigDecimal shippingCost = request.shippingCost() != null
            ? request.shippingCost().setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (shippingCost.signum() < 0) {
            throw new IllegalArgumentException("El costo de envío no puede ser negativo.");
        }

        // Lock order for every money operation: cash session, then the person who pays the first payment, then the
        // accounts of the shipping and the first payment together by id (T-14)
        PurchasePaymentRequest initialPayment = request.initialPayment();
        UUID shippingAccountId = shippingCost.signum() > 0 ? request.shippingAccountId() : null;
        UUID paymentAccountId = initialPayment != null ? initialPayment.accountId() : null;
        lockPayers(shippingAccountId, paymentAccountId);
        if (initialPayment != null && initialPayment.creditorId() != null && initialPayment.accountId() == null) {
            lockCreditor(initialPayment.creditorId());
        }
        Set<UUID> payerAccounts = new HashSet<>();
        if (shippingAccountId != null) payerAccounts.add(shippingAccountId);
        if (paymentAccountId != null) payerAccounts.add(paymentAccountId);
        if (!payerAccounts.isEmpty()) {
            financialAccountRepository.findAllByIdForUpdate(payerAccounts);
        }

        // Category and accounts are resolved before saving anything (RG-05); lock order: session, then account (T-14)
        FinancialCategory shippingCategory = null;
        Payer shippingPayer = null;
        if (shippingCost.signum() > 0) {
            if (request.shippingAccountId() == null) {
                throw new IllegalArgumentException(SHIPPING_ACCOUNT_REQUIRED);
            }
            shippingCategory = financialCategoryRepository
                .findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_SHIPPING)
                .orElseThrow(() -> new IllegalStateException(SHIPPING_CATEGORY_MISSING));
            shippingPayer = lockPayer(request.shippingAccountId());
        }

        Instant now = Instant.now();
        Purchase purchase = Purchase.builder()
            .purchaseNumber(formatPurchaseNumber(purchaseRepository.nextPurchaseNumber()))
            .supplier(supplier)
            .purchaseDate(purchaseDate)
            .status(PurchaseStatus.REGISTRADO)
            .itemsTotal(itemsTotal)
            .shippingCost(shippingCost)
            .totalAmount(itemsTotal.add(shippingCost))
            .paidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .consignment(Boolean.TRUE.equals(request.consignment()))
            .comment(request.comment() != null && !request.comment().isBlank() ? request.comment().trim() : null)
            .deliveryType(delivery.type())
            .deliveryStatus(delivery.status())
            .deliveryDate(delivery.date())
            .createdBy(username)
            .createdAt(now)
            .build();
        items.forEach(item -> item.setPurchase(purchase));
        purchase.setItems(items);
        Purchase saved = purchaseRepository.save(purchase);

        for (CostUpdate update : costUpdates) {
            applyCostUpdate(update, saved.getId(), username, now);
        }

        if (shippingPayer != null) {
            recordExpense(shippingPayer, shippingCategory, shippingCost, saved,
                "Envío pedido " + saved.getPurchaseNumber(), null, username);
        }

        // Saved in the same transaction: a rejected payment leaves no purchase (RG-05)
        if (initialPayment != null) {
            applyPayment(saved, initialPayment, username);
            saved = purchaseRepository.save(saved);
        }

        return PurchaseResponse.detail(saved, shippingPayer != null ? shippingPayer.account().getName() : null);
    }

    @Override
    public PurchaseResponse registerPayment(UUID purchaseId, PurchasePaymentRequest request, String username) {
        // Lock order: purchase, then the person or the cash session and the account (T-14)
        Purchase purchase = lockPurchase(purchaseId);
        if (purchase.getStatus() == PurchaseStatus.ANULADO) {
            throw new IllegalStateException(VOIDED_PURCHASE);
        }
        applyPayment(purchase, request, username);
        Purchase saved = purchaseRepository.save(purchase);

        return getPurchaseById(saved.getId());
    }

    /**
     * COM-05 / DEU-02: a payment from an account generates a Pedido expense; one made by an active person only
     * raises the debt with them, since no money left the business until it is repaid (DEU-03).
     */
    private void applyPayment(Purchase purchase, PurchasePaymentRequest request, String username) {
        if (request == null || (request.accountId() == null) == (request.creditorId() == null)) {
            throw new IllegalArgumentException(PAYMENT_SOURCE_REQUIRED);
        }
        BigDecimal amount = FinancialTransactionServiceImpl.normalizeAmount(request.amount());
        BigDecimal balance = purchase.getBalance();
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException("El pago supera el saldo pendiente del pedido (Bs " + balance.toPlainString() + ").");
        }

        Instant now = Instant.now();
        PurchasePayment.PurchasePaymentBuilder payment = PurchasePayment.builder()
            .purchase(purchase)
            .amount(amount)
            .status(PurchasePaymentRecordStatus.ACTIVO)
            .createdBy(username)
            .createdAt(now);

        if (request.creditorId() != null) {
            Creditor creditor = lockCreditor(request.creditorId());
            if (creditor.getStatus() != CreditorStatus.ACTIVO) {
                throw new IllegalArgumentException("La persona '" + creditor.getName() + "' no está activa.");
            }
            payment.creditor(creditor).paymentDate(now);
        } else {
            FinancialCategory paymentCategory = financialCategoryRepository
                .findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_PAYMENT)
                .orElseThrow(() -> new IllegalStateException(PAYMENT_CATEGORY_MISSING));
            Payer payer = lockPayer(request.accountId());

            String notes = request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null;
            FinancialTransaction tx = recordExpense(payer, paymentCategory, amount, purchase,
                "Pago pedido " + purchase.getPurchaseNumber(), notes, username);
            payment.account(payer.account()).paymentDate(tx.getTransactionDate()).financialTransactionId(tx.getId());
        }

        purchase.getPayments().add(payment.build());
        purchase.setPaidAmount(purchase.getPaidAmount().add(amount));
        purchase.setUpdatedBy(username);
        purchase.setUpdatedAt(now);
    }

    @Override
    public PurchaseResponse updateDelivery(UUID purchaseId, PurchaseDeliveryRequest request, String username) {
        Purchase purchase = lockPurchase(purchaseId);
        if (purchase.getStatus() == PurchaseStatus.ANULADO) {
            throw new IllegalStateException(VOIDED_PURCHASE);
        }
        if (purchase.isHistorical()) {
            throw new IllegalStateException(HISTORICAL_PURCHASE);
        }
        Delivery delivery = resolveDelivery(request.deliveryType(), request.deliveryStatus(), request.deliveryDate(),
            purchase.getPurchaseDate(), LocalDate.now(STORE_ZONE));
        purchase.setDeliveryType(delivery.type());
        purchase.setDeliveryStatus(delivery.status());
        purchase.setDeliveryDate(delivery.date());
        purchase.setUpdatedBy(username);
        purchase.setUpdatedAt(Instant.now());
        purchaseRepository.save(purchase);
        return getPurchaseById(purchaseId);
    }

    @Override
    public PurchaseResponse voidPayment(UUID purchaseId, UUID paymentId, VoidPurchaseRequest request, String username) {
        String voidReason = requireVoidReason(request);

        // Lock order: purchase, then cash session, then accounts (T-14)
        Purchase purchase = lockPurchase(purchaseId);
        if (purchase.getStatus() == PurchaseStatus.ANULADO) {
            throw new IllegalStateException(VOIDED_PURCHASE);
        }
        PurchasePayment payment = purchase.getPayments().stream()
            .filter(p -> p.getId().equals(paymentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con ID: " + paymentId));
        if (payment.getStatus() == PurchasePaymentRecordStatus.ANULADO) {
            throw new IllegalStateException(ALREADY_VOIDED_PAYMENT);
        }
        if (payment.isHistorical()) {
            throw new IllegalStateException("El pago es histórico (migrado de la planilla): no se puede anular.");
        }

        Instant now = Instant.now();
        if (payment.getCreditor() != null) {
            // DEU-02: no movement to revert; the debt drops, but never below what was already repaid
            ensureDebtCovers(lockCreditor(payment.getCreditor().getId()), payment.getAmount());
        } else {
            FinancialTransaction tx = financialTransactionRepository.findById(payment.getFinancialTransactionId())
                .orElseThrow(() -> new IllegalStateException("No se encontró el movimiento del pago."));
            voidExpenses(List.of(tx), CLOSED_SESSION_PAYMENT, voidReason, username, now);
        }
        markVoided(payment, voidReason, username, now);
        purchase.setPaidAmount(purchase.getPaidAmount().subtract(payment.getAmount()));
        purchase.setUpdatedBy(username);
        purchase.setUpdatedAt(now);
        purchaseRepository.save(purchase);

        return getPurchaseById(purchaseId);
    }

    @Override
    public PurchaseResponse voidPurchase(UUID purchaseId, VoidPurchaseRequest request, String username) {
        String voidReason = requireVoidReason(request);

        // Lock order: purchase, then cash session, then accounts (T-14)
        Purchase purchase = lockPurchase(purchaseId);
        if (purchase.getStatus() == PurchaseStatus.ANULADO) {
            throw new IllegalStateException(ALREADY_VOIDED_PURCHASE);
        }
        if (purchase.isHistorical()) {
            throw new IllegalStateException(HISTORICAL_PURCHASE);
        }

        // Lock order: purchase, then the open session of its Caja movements, then people, then accounts (T-14)
        List<FinancialTransaction> movements = financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchaseId);
        movements.stream()
            .filter(tx -> tx.getStatus() == FinancialTransactionStatus.ACTIVO && tx.getCashSessionId() != null)
            .map(FinancialTransaction::getCashSessionId)
            .distinct()
            .forEach(sessionId -> cashSessionRepository.findByIdForUpdate(sessionId)
                .filter(cs -> cs.getStatus() == CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CLOSED_SESSION_PURCHASE)));

        // Payments made by people: nothing is voided if any debt would go below 0 (DEU-02)
        Map<UUID, BigDecimal> paidByCreditor = new HashMap<>();
        purchase.getPayments().stream()
            .filter(p -> p.getStatus() == PurchasePaymentRecordStatus.ACTIVO && p.getCreditor() != null)
            .forEach(p -> paidByCreditor.merge(p.getCreditor().getId(), p.getAmount(), BigDecimal::add));
        if (!paidByCreditor.isEmpty()) {
            for (Creditor creditor : creditorRepository.findAllByIdForUpdate(paidByCreditor.keySet())) {
                ensureDebtCovers(creditor, paidByCreditor.get(creditor.getId()));
            }
        }

        // Payments and shipping: every movement linked to the purchase (P-24, COM-06)
        Instant now = Instant.now();
        voidExpenses(movements, CLOSED_SESSION_PURCHASE, voidReason, username, now);
        purchase.getPayments().stream()
            .filter(p -> p.getStatus() == PurchasePaymentRecordStatus.ACTIVO)
            .forEach(p -> markVoided(p, voidReason, username, now));

        // Product cost and price changes (COM-03) stay: they are corrected from the catalog
        purchase.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        purchase.setStatus(PurchaseStatus.ANULADO);
        purchase.setVoidedBy(username);
        purchase.setVoidedAt(now);
        purchase.setVoidReason(voidReason);
        purchase.setUpdatedBy(username);
        purchase.setUpdatedAt(now);
        purchaseRepository.save(purchase);

        return getPurchaseById(purchaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAccountResponse> getPaymentAccounts() {
        return financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .map(a -> new PaymentAccountResponse(a.getId(), a.getName(), a.getColor(), Boolean.TRUE.equals(a.getIsCashAccount()),
                Boolean.TRUE.equals(a.getIsQrAccount())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PriceSuggestionResponse suggestPrice(UUID productId, BigDecimal newCost) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        if (newCost == null || newCost.signum() < 0) {
            throw new IllegalArgumentException("El costo no puede ser negativo.");
        }
        BigDecimal cost = newCost.setScale(2, RoundingMode.HALF_UP);
        return new PriceSuggestionResponse(
            product.getId(),
            product.getBuyPrice(),
            product.getSellPrice(),
            product.getPercentageProfit(),
            cost,
            suggestedSellPrice(product.getBuyPrice(), product.getSellPrice(), cost)
        );
    }

    /**
     * Sell price that keeps the current percentage profit (COM-03): new cost × current price ÷ current cost,
     * rounded up to a whole boliviano. Null when the current cost is 0 (no percentage to keep).
     */
    static BigDecimal suggestedSellPrice(BigDecimal currentCost, BigDecimal currentSellPrice, BigDecimal newCost) {
        if (currentCost == null || currentCost.signum() <= 0) {
            return null;
        }
        return newCost.multiply(currentSellPrice)
            .divide(currentCost, 10, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.CEILING)
            .setScale(2, RoundingMode.HALF_UP);
    }

    // FIN-11: a historical purchase only accepts new payments while it has a balance (COM-08)
    static final String HISTORICAL_PURCHASE = "El pedido es histórico (migrado de la planilla): solo se le pueden registrar pagos.";

    static String formatPurchaseNumber(long sequenceValue) {
        return String.format("PED-%06d", sequenceValue);
    }

    private CostUpdate planCostUpdate(Product product, BigDecimal newCost, BigDecimal requestedSellPrice, Set<UUID> updatedProducts) {
        if (!updatedProducts.add(product.getId())) {
            throw new IllegalArgumentException("El producto '" + product.getName() + "' tiene más de una línea que actualiza su costo.");
        }
        // The catalog requires a cost greater than 0 (ProductRequest)
        if (newCost.signum() <= 0) {
            throw new IllegalArgumentException("El costo de '" + product.getName() + "' debe ser mayor a 0 para actualizar el producto.");
        }
        BigDecimal newSellPrice = requestedSellPrice != null
            ? requestedSellPrice.setScale(2, RoundingMode.HALF_UP)
            : product.getSellPrice().setScale(2, RoundingMode.HALF_UP);
        if (newSellPrice.signum() <= 0) {
            throw new IllegalArgumentException("El precio de venta de '" + product.getName() + "' debe ser mayor a 0.");
        }
        return new CostUpdate(product, newCost, newSellPrice);
    }

    private void applyCostUpdate(CostUpdate update, UUID purchaseId, String username, Instant now) {
        Product product = update.product();
        productPriceChangeRepository.save(ProductPriceChange.builder()
            .productId(product.getId())
            .previousBuyPrice(product.getBuyPrice())
            .newBuyPrice(update.newCost())
            .previousSellPrice(product.getSellPrice())
            .newSellPrice(update.newSellPrice())
            .source(PriceChangeSource.PURCHASE)
            .purchaseId(purchaseId)
            .changedBy(username)
            .changedAt(now)
            .build());

        BigDecimal fixedProfit = ProductServiceImpl.calculateFixedProfit(update.newCost(), update.newSellPrice());
        product.setBuyPrice(update.newCost());
        product.setSellPrice(update.newSellPrice());
        product.setFixedProfit(fixedProfit);
        product.setPercentageProfit(ProductServiceImpl.calculatePercentageProfit(update.newCost(), fixedProfit));
        product.setUpdatedBy(username);
        product.setUpdatedAt(now);
        productRepository.save(product);
    }

    private Creditor lockCreditor(UUID creditorId) {
        return creditorRepository.findByIdForUpdate(creditorId)
            .orElseThrow(() -> new ResourceNotFoundException("Persona no encontrada con ID: " + creditorId));
    }

    // Voiding a payment made by a person lowers their debt; it cannot go below 0 (DEU-02)
    private void ensureDebtCovers(Creditor creditor, BigDecimal amount) {
        if (creditorRepository.computeDebt(creditor.getId()).compareTo(amount) < 0) {
            throw new IllegalStateException("Ya se devolvió a " + creditor.getName()
                + " parte de este pago; anule primero la devolución.");
        }
    }

    private Purchase lockPurchase(UUID purchaseId) {
        return purchaseRepository.findByIdForUpdate(purchaseId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + purchaseId));
    }

    // Every void needs a reason (RG-03), also when the service is called without bean validation
    private static String requireVoidReason(VoidPurchaseRequest request) {
        if (request == null || request.voidReason() == null || request.voidReason().isBlank()) {
            throw new IllegalArgumentException(VOID_REASON_REQUIRED);
        }
        return request.voidReason().trim();
    }

    /**
     * COM-07: no type means in store and no status means pending. While pending the date is the expected one (it
     * may be future); once delivered it defaults to today and must fall between the purchase date and today.
     */
    private static Delivery resolveDelivery(DeliveryType type, DeliveryStatus status, LocalDate date,
                                    LocalDate purchaseDate, LocalDate today) {
        DeliveryType deliveryType = type != null ? type : DeliveryType.EN_TIENDA;
        DeliveryStatus deliveryStatus = status != null ? status : DeliveryStatus.PENDIENTE;
        LocalDate deliveryDate = date;
        if (deliveryStatus == DeliveryStatus.ENTREGADO) {
            if (deliveryDate == null) {
                deliveryDate = today;
            }
            if (deliveryDate.isAfter(today)) {
                throw new IllegalArgumentException(FUTURE_DELIVERY_DATE);
            }
        }
        if (deliveryDate != null && deliveryDate.isBefore(purchaseDate)) {
            throw new IllegalArgumentException(DELIVERY_BEFORE_PURCHASE);
        }
        return new Delivery(deliveryType, deliveryStatus, deliveryDate);
    }

    // COM-09: voids the active expenses and gives each account its amount back. A Caja movement needs its own
    // session still open (CAJ-06, T-16); since only one session is open, at most one is locked. The purchase is
    // already locked, then the session, then the accounts in one query (T-14).
    private void voidExpenses(List<FinancialTransaction> txs, String closedSessionMessage, String voidReason,
                              String username, Instant now) {
        List<FinancialTransaction> active = txs.stream()
            .filter(tx -> tx.getStatus() == FinancialTransactionStatus.ACTIVO)
            .toList();
        if (active.isEmpty()) {
            return;
        }

        CashSession session = null;
        Set<UUID> sessionIds = new HashSet<>();
        active.stream().map(FinancialTransaction::getCashSessionId).filter(Objects::nonNull).forEach(sessionIds::add);
        for (UUID sessionId : sessionIds) {
            session = cashSessionRepository.findByIdForUpdate(sessionId)
                .filter(s -> s.getStatus() == CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(closedSessionMessage));
        }

        Set<UUID> accountIds = new HashSet<>();
        active.forEach(tx -> accountIds.add(tx.getSourceAccount().getId()));
        Map<UUID, FinancialAccount> accounts = new HashMap<>();
        financialAccountRepository.findAllByIdForUpdate(accountIds).forEach(a -> accounts.put(a.getId(), a));

        for (FinancialTransaction tx : active) {
            FinancialAccount account = accounts.getOrDefault(tx.getSourceAccount().getId(), tx.getSourceAccount());
            account.setCurrentBalance(account.getCurrentBalance().add(tx.getAmount()));
            financialAccountRepository.save(account);

            tx.setStatus(FinancialTransactionStatus.ANULADO);
            tx.setVoidedBy(username);
            tx.setVoidedAt(now);
            tx.setVoidReason(voidReason);
            financialTransactionRepository.save(tx);
        }

        if (session != null) {
            // Flushes first so the repository sums see the voided movements
            financialTransactionRepository.flush();
            expectedCashCalculator.refresh(session);
            cashSessionRepository.save(session);
        }
    }

    private static void markVoided(PurchasePayment payment, String voidReason, String username, Instant now) {
        payment.setStatus(PurchasePaymentRecordStatus.ANULADO);
        payment.setVoidedBy(username);
        payment.setVoidedAt(now);
        payment.setVoidReason(voidReason);
    }

    // The open session is locked first when any of the accounts is Caja (T-14)
    private void lockPayers(UUID... accountIds) {
        for (UUID accountId : accountIds) {
            if (accountId != null && financialAccountRepository.existsByIdAndIsCashAccountTrue(accountId)) {
                cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
                    .orElseThrow(() -> new IllegalStateException(CASH_SESSION_REQUIRED));
                return;
            }
        }
    }

    private Payer lockPayer(UUID accountId) {
        CashSession session = null;
        if (financialAccountRepository.existsByIdAndIsCashAccountTrue(accountId)) {
            session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CASH_SESSION_REQUIRED));
        }
        FinancialAccount account = financialAccountRepository.findActiveByIdForUpdate(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + accountId));
        return new Payer(account, session);
    }

    // Expense of a purchase (COM-06, P-24): GASTO_MANUAL so balances, summaries and the expected cash treat it
    // like any other expense; purchase_id keeps it out of Finanzas and POS edits and voids
    private FinancialTransaction recordExpense(Payer payer, FinancialCategory category, BigDecimal amount,
                                               Purchase purchase, String description, String notes, String username) {
        FinancialAccount account = payer.account();
        account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
        financialAccountRepository.save(account);

        Instant now = Instant.now();
        FinancialTransaction tx = financialTransactionRepository.save(FinancialTransaction.builder()
            .type(FinancialTransactionType.GASTO_MANUAL)
            .sourceAccount(account)
            .category(category)
            .amount(amount)
            .transactionDate(now)
            .description(description)
            .notes(notes)
            .status(FinancialTransactionStatus.ACTIVO)
            .purchaseId(purchase.getId())
            .cashSessionId(payer.session() != null ? payer.session().getId() : null)
            .createdBy(username)
            .createdAt(now)
            .build());

        if (payer.session() != null) {
            expectedCashCalculator.refresh(payer.session());
            cashSessionRepository.save(payer.session());
        }
        return tx;
    }
}
