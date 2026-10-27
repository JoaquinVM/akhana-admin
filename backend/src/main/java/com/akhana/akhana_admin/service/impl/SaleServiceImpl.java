package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.CreditorRepository;
import com.akhana.akhana_admin.repository.SaleItemRepository;
import com.akhana.akhana_admin.repository.CustomerRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.ProductPresentationService;
import com.akhana.akhana_admin.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final CashSessionRepository cashSessionRepository;
    private final ProductRepository productRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ExpectedCashCalculator expectedCashCalculator;
    private final CustomerRepository customerRepository;
    private final ProductPresentationService presentationService;
    private final SaleItemRepository saleItemRepository;
    private final CreditorRepository creditorRepository;

    static final String VOID_REASON_REQUIRED = "El motivo de anulación es obligatorio.";
    static final String SALES_CATEGORY_MISSING = "No existe la categoría Venta; no se puede registrar la venta.";
    static final String CASH_ACCOUNT_MISSING = "No existe la cuenta Caja; no se puede registrar el cobro en efectivo.";
    static final String QR_ACCOUNT_MISSING = "No hay una cuenta marcada para cobros QR; no se puede registrar el cobro QR.";
    static final String AT_COST_NO_DISCOUNTS = "Una venta al costo no admite descuentos.";
    static final String CREDIT_ONLY_AT_COST = "Solo una venta al costo puede quedar a crédito.";
    static final String CREDIT_CUSTOMER_REQUIRED = "Elija un cliente activo para la venta a crédito.";
    static final String CREDIT_OUT_OF_RANGE = "El crédito debe estar entre Bs 0,00 y el total de la venta.";
    static final String CREDIT_METHOD_INCOMPLETE = "El pago no cubre el total: lo cobrado más el crédito debe ser igual al total de la venta.";
    static final String FREE_NAME_REQUIRED = "Escriba el nombre de la línea libre.";
    static final String FREE_LINE_ONLY_SPECIAL = "Solo un producto especial lleva nombre y precio escritos.";
    static final String FREE_LINE_NO_DISCOUNT = "Una línea libre no admite descuentos.";
    static final String FREE_LINES_NO_GLOBAL_DISCOUNT = "Una venta con líneas libres no admite descuento general.";
    static final String AT_COST_NO_FREE_LINES = "Una venta al costo no admite líneas libres.";
    static final String ALREADY_ASSIGNED = "La línea ya fue asignada.";
    static final String VOIDED_SALE_ASSIGNMENT = "Una venta anulada no se asigna.";
    static final String ASSIGNABLE_PRODUCT_REQUIRED = "Elija un producto activo que se venda por unidad.";
    static final int FREE_NAME_MAX = 150;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Override
    public SaleResponse registerSale(SaleDetailRequest request, String username) {
        // Lock order for every money operation: cash session, then customer, then accounts by id (T-14)
        CashSession session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new IllegalStateException("Para registrar una venta debe existir una caja abierta."));

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("La venta debe contener al menos un producto.");
        }

        SaleType saleType = request.saleType() != null ? request.saleType() : SaleType.NORMAL;
        boolean atCost = saleType == SaleType.AL_COSTO;

        BigDecimal subtotalGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountItemsTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<SaleItem> saleItems = new ArrayList<>();
        boolean hasFreeLines = false;

        for (SaleItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + itemReq.productId()));

            if (product.getStatus() != ProductStatus.ACTIVO) {
                throw new IllegalArgumentException("El producto '" + product.getName() + "' no se encuentra activo para la venta.");
            }

            if (product.isSpecial()) {
                if (atCost) {
                    throw new IllegalArgumentException(AT_COST_NO_FREE_LINES);
                }
                hasFreeLines = true;
                saleItems.add(buildFreeLine(product, itemReq));
                continue;
            }
            if (itemReq.freeName() != null || itemReq.freePrice() != null) {
                throw new IllegalArgumentException(FREE_LINE_ONLY_SPECIAL);
            }

            // CAT-11: by presentation the quantity is whole packs and the kilos are packs x presentation weight
            ProductPresentation presentation = itemReq.presentationId() != null
                ? presentationService.findActivePresentation(product, itemReq.presentationId())
                : null;
            BigDecimal qty = presentation != null
                ? normalizePackQuantity(itemReq.quantity(), product.getName(), presentation.getName())
                : normalizeQuantity(itemReq.quantity(), product.getName(), product.getSaleUnit());
            // Cost snapshot (POS-05): later cost changes do not alter this sale's profit
            BigDecimal unitCost = product.getBuyPrice() != null
                ? product.getBuyPrice().setScale(2, RoundingMode.HALF_UP)
                : ZERO;
            BigDecimal discountPerUnit = itemReq.discountPerUnit() != null 
                ? itemReq.discountPerUnit().setScale(2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            // At cost the server sets price = cost, whatever the client sent (CON-02)
            if (atCost) {
                if (unitCost.signum() <= 0) {
                    throw new IllegalArgumentException("'" + product.getName() + "' no tiene costo registrado; no se puede vender al costo.");
                }
                if (discountPerUnit.signum() != 0) {
                    throw new IllegalArgumentException(AT_COST_NO_DISCOUNTS);
                }
            }
            // A presentation at cost costs weight x cost per kilo: its fixed price is ignored (CAT-11)
            BigDecimal unitPrice;
            if (presentation != null) {
                unitPrice = atCost
                    ? ProductPresentationServiceImpl.costOf(presentation, product)
                    : ProductPresentationServiceImpl.priceOf(presentation, product);
            } else {
                unitPrice = atCost ? unitCost : product.getSellPrice().setScale(2, RoundingMode.HALF_UP);
            }

            if (discountPerUnit.compareTo(BigDecimal.ZERO) < 0 || discountPerUnit.compareTo(unitPrice) > 0) {
                throw new IllegalArgumentException("El descuento por unidad para '" + product.getName() + "' no puede ser negativo ni superar su precio.");
            }

            BigDecimal finalUnitPrice = unitPrice.subtract(discountPerUnit).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineSubtotal = finalUnitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineGross = unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            // Derived from the rounded line amounts so gross - discount always equals the line subtotal
            BigDecimal lineDiscount = lineGross.subtract(lineSubtotal);

            subtotalGross = subtotalGross.add(lineGross);
            discountItemsTotal = discountItemsTotal.add(lineDiscount);

            SaleItem item = SaleItem.builder()
                .product(product)
                .productName(product.getName())
                .productCode(product.getCode())
                .unitPrice(unitPrice)
                .unitCost(unitCost)
                .discountPerUnit(discountPerUnit)
                .finalUnitPrice(finalUnitPrice)
                .quantity(qty)
                .presentation(presentation)
                .presentationName(presentation != null ? presentation.getName() : null)
                .presentationWeightKg(presentation != null ? presentation.getWeightKg() : null)
                .weightKg(presentation != null ? packWeight(qty, presentation) : null)
                .subtotal(lineSubtotal)
                .build();

            saleItems.add(item);
        }

        // Free lines have no discount, so their subtotal is the gross amount
        for (SaleItem item : saleItems) {
            if (item.getFreeLineType() != null) {
                subtotalGross = subtotalGross.add(item.getSubtotal());
            }
        }

        BigDecimal subtotalPostItems = subtotalGross.subtract(discountItemsTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal globalDiscount = request.globalDiscountAmount() != null 
            ? request.globalDiscountAmount().setScale(2, RoundingMode.HALF_UP) 
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // POS-11: each free line's amount is exact for the owner's debt and the reports
        if (hasFreeLines && globalDiscount.signum() != 0) {
            throw new IllegalArgumentException(FREE_LINES_NO_GLOBAL_DISCOUNT);
        }

        if (atCost && globalDiscount.signum() != 0) {
            throw new IllegalArgumentException(AT_COST_NO_DISCOUNTS);
        }
        if (globalDiscount.compareTo(BigDecimal.ZERO) < 0 || globalDiscount.compareTo(subtotalPostItems) > 0) {
            throw new IllegalArgumentException("El descuento general no puede ser negativo ni superar el total de la venta.");
        }

        BigDecimal discountTotal = discountItemsTotal.add(globalDiscount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotalPostItems.subtract(globalDiscount).setScale(2, RoundingMode.HALF_UP);

        // Credit only on at-cost sales (DEU-05, P-12); the payment method covers the part paid now
        BigDecimal creditAmount = request.creditAmount() != null
            ? request.creditAmount().setScale(2, RoundingMode.HALF_UP)
            : ZERO;
        if (creditAmount.signum() > 0 && !atCost) {
            throw new IllegalArgumentException(CREDIT_ONLY_AT_COST);
        }
        if (creditAmount.signum() < 0 || creditAmount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException(CREDIT_OUT_OF_RANGE);
        }
        BigDecimal paidNow = totalAmount.subtract(creditAmount).setScale(2, RoundingMode.HALF_UP);
        PaymentMethod paymentMethod = request.paymentMethod();
        if (creditAmount.signum() > 0 && paidNow.signum() == 0) {
            // Nothing paid now: the whole sale is on credit
            paymentMethod = PaymentMethod.CREDITO;
        } else if (paymentMethod == PaymentMethod.CREDITO) {
            throw new IllegalArgumentException(CREDIT_METHOD_INCOMPLETE);
        }

        BigDecimal amountCash = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountQr = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountReceived = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal changeGiven = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (paymentMethod == PaymentMethod.EFECTIVO) {
            amountCash = paidNow;
            amountReceived = request.amountReceived() != null ? request.amountReceived().setScale(2, RoundingMode.HALF_UP) : paidNow;
            if (amountReceived.compareTo(paidNow) < 0) {
                throw new IllegalArgumentException("El monto recibido en efectivo no puede ser menor al total.");
            }
            changeGiven = amountReceived.subtract(paidNow).setScale(2, RoundingMode.HALF_UP);
        } else if (paymentMethod == PaymentMethod.QR) {
            amountQr = paidNow;
            amountReceived = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            changeGiven = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (paymentMethod == PaymentMethod.MIXTO) {
            BigDecimal cashPortion = request.amountCash() != null ? request.amountCash().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (cashPortion.compareTo(BigDecimal.ZERO) < 0 || cashPortion.compareTo(paidNow) > 0) {
                throw new IllegalArgumentException("En pago mixto, la porción en efectivo debe estar entre 0 y el total.");
            }
            amountCash = cashPortion;
            amountQr = paidNow.subtract(cashPortion).setScale(2, RoundingMode.HALF_UP);
            amountReceived = request.amountReceived() != null ? request.amountReceived().setScale(2, RoundingMode.HALF_UP) : cashPortion;
            if (amountReceived.compareTo(cashPortion) < 0) {
                throw new IllegalArgumentException("El monto recibido en efectivo no puede ser menor a la porción pactada en efectivo.");
            }
            changeGiven = amountReceived.subtract(cashPortion).setScale(2, RoundingMode.HALF_UP);
        }

        // The customer is locked after the session so its receivable does not change meanwhile (T-14)
        Customer customer = null;
        if (atCost && (request.customerId() != null || creditAmount.signum() > 0)) {
            if (request.customerId() == null) {
                throw new IllegalArgumentException(CREDIT_CUSTOMER_REQUIRED);
            }
            customer = customerRepository.findByIdForUpdate(request.customerId())
                .filter(c -> c.getStatus() == CustomerStatus.ACTIVO)
                .orElseThrow(() -> new IllegalArgumentException(CREDIT_CUSTOMER_REQUIRED));
        }

        // The sale fails if its category or accounts are missing (RG-05); accounts are locked after the session (T-14)
        FinancialCategory salesCategory = financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()
            .orElseThrow(() -> new IllegalStateException(SALES_CATEGORY_MISSING));
        List<FinancialAccount> saleAccounts = financialAccountRepository.findSaleAccountsForUpdate();
        FinancialAccount cashAccount = null;
        if (amountCash.compareTo(BigDecimal.ZERO) > 0) {
            cashAccount = saleAccounts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsCashAccount()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(CASH_ACCOUNT_MISSING));
        }
        FinancialAccount qrAccount = null;
        if (amountQr.compareTo(BigDecimal.ZERO) > 0) {
            qrAccount = saleAccounts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsQrAccount()) && a.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(QR_ACCOUNT_MISSING));
        }

        String saleNumber = formatSaleNumber(saleRepository.nextSaleNumber());

        Sale sale = Sale.builder()
            .saleNumber(saleNumber)
            .cashSession(session)
            .status(SaleStatus.COMPLETADA)
            .saleType(saleType)
            .customer(customer)
            .creditAmount(creditAmount)
            .subtotalAmount(subtotalGross)
            .discountItemsTotal(discountItemsTotal)
            .globalDiscountAmount(globalDiscount)
            .discountTotal(discountTotal)
            .totalAmount(totalAmount)
            .paymentMethod(paymentMethod)
            .amountCash(amountCash)
            .amountQr(amountQr)
            .amountReceived(amountReceived)
            .changeGiven(changeGiven)
            .description(request.description() != null ? request.description().trim() : null)
            .createdBy(username)
            .createdAt(Instant.now())
            .build();

        for (SaleItem item : saleItems) {
            item.setSale(sale);
        }
        sale.setItems(saleItems);

        Sale savedSale = saleRepository.save(sale);

        // Actualizar métricas acumuladas de la caja activa
        session.setTotalSalesCash(session.getTotalSalesCash().add(amountCash));
        session.setTotalSalesQr(session.getTotalSalesQr().add(amountQr));
        session.setTotalSales(session.getTotalSales().add(totalAmount));
        expectedCashCalculator.refresh(session);

        cashSessionRepository.save(session);

        // Generar movimientos financieros automáticos por venta
        if (cashAccount != null) {
            recordSaleIncome(cashAccount, salesCategory, amountCash, savedSale, "Efectivo", username);
        }
        if (qrAccount != null) {
            recordSaleIncome(qrAccount, salesCategory, amountQr, savedSale, "QR", username);
        }

        return SaleResponse.fromEntity(savedSale);
    }

    @Override
    public SaleResponse voidSale(UUID saleId, VoidSaleRequest request, String username) {
        // Every void needs a reason (RG-03), also when the service is called without bean validation
        if (request == null || request.voidReason() == null || request.voidReason().isBlank()) {
            throw new IllegalArgumentException(VOID_REASON_REQUIRED);
        }
        String voidReason = request.voidReason().trim();

        // The open session is locked before the sale is read, so a second void sees it already voided
        Optional<CashSession> openSession = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA);

        Sale sale = saleRepository.findById(saleId)
            .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con ID: " + saleId));

        if (sale.getStatus() == SaleStatus.ANULADA) {
            throw new IllegalStateException("La venta ya ha sido anulada previamente.");
        }

        CashSession session = openSession
            .filter(open -> open.getId().equals(sale.getCashSession().getId()))
            .orElseThrow(() -> new IllegalStateException("Solo se pueden anular ventas pertenecientes a una caja abierta."));

        // Voiding a credit sale lowers the receivable, which cannot go below 0 (DEU-05); customer after session (T-14)
        if (sale.getCreditAmount() != null && sale.getCreditAmount().signum() > 0 && sale.getCustomer() != null) {
            Customer customer = customerRepository.findByIdForUpdate(sale.getCustomer().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + sale.getCustomer().getId()));
            BigDecimal balance = Optional.ofNullable(customerRepository.computeBalance(customer.getId())).orElse(BigDecimal.ZERO);
            if (balance.subtract(sale.getCreditAmount()).signum() < 0) {
                throw new IllegalStateException("Ya se cobró parte de esta venta a " + customer.getName()
                    + "; anule primero el cobro.");
            }
        }

        // Voiding lowers the debt with the owners of its third-party lines, which cannot go below 0 (POS-13)
        Map<UUID, BigDecimal> owed = new TreeMap<>();
        for (SaleItem item : sale.getItems()) {
            if (item.getCreditor() != null) {
                owed.merge(item.getCreditor().getId(), item.getSubtotal(), BigDecimal::add);
            }
        }
        if (!owed.isEmpty()) {
            for (Creditor owner : creditorRepository.findAllByIdForUpdate(owed.keySet())) {
                BigDecimal debt = Optional.ofNullable(creditorRepository.computeDebt(owner.getId())).orElse(BigDecimal.ZERO);
                if (debt.subtract(owed.get(owner.getId())).signum() < 0) {
                    throw new IllegalStateException("Ya se devolvió a " + owner.getName()
                        + " parte de esta venta; anule primero la devolución.");
                }
            }
        }

        sale.setStatus(SaleStatus.ANULADA);
        sale.setVoidedAt(Instant.now());
        sale.setVoidedBy(username);
        sale.setVoidReason(voidReason);

        // Revertir acumuladores financieros de la caja abierta
        session.setTotalSalesCash(session.getTotalSalesCash().subtract(sale.getAmountCash()));
        session.setTotalSalesQr(session.getTotalSalesQr().subtract(sale.getAmountQr()));
        session.setTotalSales(session.getTotalSales().subtract(sale.getTotalAmount()));
        expectedCashCalculator.refresh(session);

        cashSessionRepository.save(session);
        Sale updatedSale = saleRepository.save(sale);

        // Anular transacciones financieras generadas por la venta
        List<FinancialTransaction> relatedTxs = financialTransactionRepository.findBySaleId(saleId);
        Set<UUID> accountIds = relatedTxs.stream()
            .filter(tx -> tx.getSourceAccount() != null)
            .map(tx -> tx.getSourceAccount().getId())
            .collect(Collectors.toSet());
        if (!accountIds.isEmpty()) {
            financialAccountRepository.findAllByIdForUpdate(accountIds);
        }
        for (FinancialTransaction tx : relatedTxs) {
            if (tx.getStatus() == FinancialTransactionStatus.ACTIVO) {
                tx.setStatus(FinancialTransactionStatus.ANULADO);
                tx.setVoidedAt(Instant.now());
                tx.setVoidedBy(username);
                tx.setVoidReason(voidReason);

                FinancialAccount acc = tx.getSourceAccount();
                if (acc != null) {
                    acc.setCurrentBalance(acc.getCurrentBalance().subtract(tx.getAmount()));
                    financialAccountRepository.save(acc);
                }
                financialTransactionRepository.save(tx);
            }
        }

        return SaleResponse.fromEntity(updatedSale);
    }

    // POS-11: a special product sold with the name and price typed in the POS. Unidentified lines wait for their
    // product with cost 0 (POS-12); third-party lines cost their price and are owed to the owner (POS-13)
    private static SaleItem buildFreeLine(Product product, SaleItemRequest itemReq) {
        String freeName = itemReq.freeName() != null ? itemReq.freeName().trim() : "";
        if (freeName.isEmpty()) {
            throw new IllegalArgumentException(FREE_NAME_REQUIRED);
        }
        if (freeName.length() > FREE_NAME_MAX) {
            throw new IllegalArgumentException("El nombre de la línea libre no debe superar los 150 caracteres.");
        }
        BigDecimal price = itemReq.freePrice();
        if (price == null || price.signum() <= 0 || price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El precio de '" + freeName + "' debe ser mayor a 0, con hasta 2 decimales.");
        }
        if (itemReq.presentationId() != null) {
            throw new IllegalArgumentException("'" + freeName + "' no se vende por presentación.");
        }
        if (itemReq.discountPerUnit() != null && itemReq.discountPerUnit().signum() != 0) {
            throw new IllegalArgumentException(FREE_LINE_NO_DISCOUNT);
        }
        BigDecimal unitPrice = price.setScale(2, RoundingMode.HALF_UP);
        BigDecimal qty = normalizeQuantity(itemReq.quantity(), freeName, SaleUnit.UNIT);
        boolean thirdParty = product.getSpecialType() == ProductSpecialType.THIRD_PARTY;
        return SaleItem.builder()
            .product(product)
            .productName(freeName)
            .productCode(product.getCode())
            .unitPrice(unitPrice)
            .unitCost(thirdParty ? unitPrice : ZERO)
            .discountPerUnit(ZERO)
            .finalUnitPrice(unitPrice)
            .quantity(qty)
            .subtotal(unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP))
            .freeLineType(product.getSpecialType())
            .freeName(freeName)
            .assignmentStatus(thirdParty ? null : SaleItemAssignmentStatus.POR_ASIGNAR)
            .creditor(thirdParty ? product.getOwnerCreditor() : null)
            .build();
    }

    @Override
    public SaleResponse assignItem(UUID saleId, UUID itemId, UUID productId, String username) {
        SaleItem item = lockPendingItem(saleId, itemId);
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        ProductServiceImpl.ensureNotSpecial(product);
        if (product.getStatus() != ProductStatus.ACTIVO || product.getSaleUnit() != SaleUnit.UNIT) {
            throw new IllegalArgumentException(ASSIGNABLE_PRODUCT_REQUIRED);
        }
        // The only change of a line's cost (T-07): the real product's current cost; amounts and cash stay as sold
        saleItemRepository.assign(item.getId(), product, product.getCode(), product.getName(),
            product.getBuyPrice().setScale(2, RoundingMode.HALF_UP), SaleItemAssignmentStatus.ASIGNADO, username, Instant.now());
        return getSaleById(saleId);
    }

    @Override
    public SaleResponse closeUnidentified(UUID saleId, UUID itemId, String username) {
        SaleItem item = lockPendingItem(saleId, itemId);
        saleItemRepository.assign(item.getId(), item.getProduct(), item.getProductCode(), item.getProductName(), ZERO,
            SaleItemAssignmentStatus.SIN_IDENTIFICAR, username, Instant.now());
        return getSaleById(saleId);
    }

    // The line is locked so two people never assign it at the same time (POS-12)
    private SaleItem lockPendingItem(UUID saleId, UUID itemId) {
        SaleItem item = saleItemRepository.findByIdForUpdate(itemId)
            .filter(i -> i.getSale().getId().equals(saleId))
            .orElseThrow(() -> new ResourceNotFoundException("Línea de venta no encontrada con ID: " + itemId));
        if (item.getFreeLineType() != ProductSpecialType.UNIDENTIFIED) {
            throw new IllegalArgumentException("Solo se asigna una línea de producto sin identificar.");
        }
        if (item.getAssignmentStatus() != SaleItemAssignmentStatus.POR_ASIGNAR) {
            throw new IllegalStateException(ALREADY_ASSIGNED);
        }
        if (item.getSale().getStatus() == SaleStatus.ANULADA) {
            throw new IllegalStateException(VOIDED_SALE_ASSIGNMENT);
        }
        return item;
    }

    private void recordSaleIncome(FinancialAccount account, FinancialCategory category, BigDecimal amount,
                                  Sale sale, String paymentLabel, String username) {
        account.setCurrentBalance(account.getCurrentBalance().add(amount));
        financialAccountRepository.save(account);

        FinancialTransaction tx = FinancialTransaction.builder()
            .type(FinancialTransactionType.VENTA_INGRESO)
            .sourceAccount(account)
            .category(category)
            .amount(amount)
            .transactionDate(sale.getCreatedAt())
            .description("Venta #" + sale.getSaleNumber() + " (" + paymentLabel + ")")
            .status(FinancialTransactionStatus.ACTIVO)
            .saleId(sale.getId())
            .createdBy(username)
            .createdAt(Instant.now())
            .build();
        financialTransactionRepository.save(tx);
    }

    // Quantities may be decimal for products sold by weight (CAT-08): more than 0, up to 3 decimals;
    // products sold by unit only take whole quantities
    static BigDecimal normalizeQuantity(BigDecimal quantity, String productName, SaleUnit saleUnit) {
        if (quantity == null) {
            return BigDecimal.ONE.setScale(3);
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad de '" + productName + "' debe ser mayor a 0.");
        }
        if (quantity.stripTrailingZeros().scale() > 3) {
            throw new IllegalArgumentException("La cantidad de '" + productName + "' admite hasta 3 decimales.");
        }
        if (saleUnit != SaleUnit.KILOGRAM && quantity.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("La cantidad de '" + productName + "' debe ser entera: se vende por unidad.");
        }
        return quantity.setScale(3);
    }

    // A line by presentation counts whole packs (CAT-11)
    static BigDecimal normalizePackQuantity(BigDecimal quantity, String productName, String presentationName) {
        String label = productName + " · " + presentationName;
        if (quantity == null) {
            return BigDecimal.ONE.setScale(3);
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad de '" + label + "' debe ser mayor a 0.");
        }
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("La cantidad de '" + label + "' debe ser entera: se vende por presentación.");
        }
        return quantity.setScale(3);
    }

    static BigDecimal packWeight(BigDecimal packs, ProductPresentation presentation) {
        return packs.multiply(presentation.getWeightKg()).setScale(3, RoundingMode.HALF_UP);
    }

    static String formatSaleNumber(long sequenceValue) {
        return String.format("VTA-%06d", sequenceValue);
    }

    static final int MAX_PAGE_SIZE = 100;
    // Open bounds of the history when a date is missing: before and after any real sale
    private static final Instant NO_FROM = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant NO_TO = Instant.parse("3000-01-01T00:00:00Z");

    @Override
    @Transactional(readOnly = true)
    public Page<SaleResponse> searchSales(LocalDate from, LocalDate to, SaleStatus status, SaleType saleType,
                                          PaymentMethod paymentMethod, String createdBy, boolean pendingAssignment,
                                          int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        // Dates are store days (America/La_Paz); "to" includes the whole day
        Instant fromInstant = from != null ? from.atStartOfDay(PurchaseServiceImpl.STORE_ZONE).toInstant() : NO_FROM;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(PurchaseServiceImpl.STORE_ZONE).toInstant() : NO_TO;
        String seller = createdBy != null && !createdBy.isBlank() ? createdBy.trim() : null;
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return saleRepository.search(fromInstant, toInstant, status, saleType, paymentMethod, seller, pendingAssignment,
                pageRequest)
            .map(SaleResponse::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getSellers() {
        return saleRepository.findSellers();
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getSaleById(UUID saleId) {
        Sale sale = saleRepository.findById(saleId)
            .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con ID: " + saleId));
        return SaleResponse.fromEntity(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByCashSession(UUID cashSessionId) {
        return saleRepository.findByCashSessionIdOrderByCreatedAtDesc(cashSessionId).stream()
            .map(SaleResponse::fromEntity)
            .collect(Collectors.toList());
    }
}
