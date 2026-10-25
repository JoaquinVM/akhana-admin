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

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final ProductPriceChangeRepository productPriceChangeRepository;
    private final CashSessionRepository cashSessionRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ExpectedCashCalculator expectedCashCalculator;

    /** Product cost and price change confirmed on a purchase line (COM-03), applied after the purchase is saved. */
    private record CostUpdate(Product product, BigDecimal newCost, BigDecimal newSellPrice) {}

    /** Locked cash session (only for Caja) and account that pay a movement. */
    private record Payer(FinancialAccount account, CashSession session) {}

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseResponse> getPurchases(UUID supplierId, PurchasePaymentStatus paymentStatus, String search) {
        String term = search != null && !search.isBlank() ? search.trim() : null;
        return purchaseRepository.search(supplierId, term).stream()
            .filter(p -> paymentStatus == null || p.getPaymentStatus() == paymentStatus)
            .map(PurchaseResponse::summary)
            .toList();
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

        return PurchaseResponse.detail(saved, shippingPayer != null ? shippingPayer.account().getName() : null);
    }

    @Override
    public PurchaseResponse registerPayment(UUID purchaseId, PurchasePaymentRequest request, String username) {
        BigDecimal amount = FinancialTransactionServiceImpl.normalizeAmount(request.amount());

        // Lock order: purchase, then cash session, then account (T-14)
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + purchaseId));
        if (purchase.getStatus() == PurchaseStatus.ANULADO) {
            throw new IllegalStateException(VOIDED_PURCHASE);
        }
        BigDecimal balance = purchase.getBalance();
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException("El pago supera el saldo pendiente del pedido (Bs " + balance.toPlainString() + ").");
        }

        FinancialCategory paymentCategory = financialCategoryRepository
            .findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_PAYMENT)
            .orElseThrow(() -> new IllegalStateException(PAYMENT_CATEGORY_MISSING));
        Payer payer = lockPayer(request.accountId());

        String notes = request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null;
        FinancialTransaction tx = recordExpense(payer, paymentCategory, amount, purchase,
            "Pago pedido " + purchase.getPurchaseNumber(), notes, username);

        PurchasePayment payment = PurchasePayment.builder()
            .purchase(purchase)
            .account(payer.account())
            .amount(amount)
            .paymentDate(tx.getTransactionDate())
            .financialTransactionId(tx.getId())
            .status(PurchasePaymentRecordStatus.ACTIVO)
            .createdBy(username)
            .createdAt(Instant.now())
            .build();
        purchase.getPayments().add(payment);
        purchase.setPaidAmount(purchase.getPaidAmount().add(amount));
        purchase.setUpdatedBy(username);
        purchase.setUpdatedAt(Instant.now());
        Purchase saved = purchaseRepository.save(purchase);

        return getPurchaseById(saved.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAccountResponse> getPaymentAccounts() {
        return financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .map(a -> new PaymentAccountResponse(a.getId(), a.getName(), a.getColor(), Boolean.TRUE.equals(a.getIsCashAccount())))
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
