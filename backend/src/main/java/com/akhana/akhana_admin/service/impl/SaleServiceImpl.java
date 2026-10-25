package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    @Override
    public SaleResponse registerSale(SaleDetailRequest request, String username) {
        // Lock order for every money operation: cash session first, then accounts by id (T-14)
        CashSession session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new IllegalStateException("Para registrar una venta debe existir una caja abierta."));

        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("La venta debe contener al menos un producto.");
        }

        BigDecimal subtotalGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountItemsTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<SaleItem> saleItems = new ArrayList<>();

        for (SaleItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + itemReq.productId()));

            if (product.getStatus() != ProductStatus.ACTIVO) {
                throw new IllegalArgumentException("El producto '" + product.getName() + "' no se encuentra activo para la venta.");
            }

            int qty = itemReq.quantity() != null && itemReq.quantity() > 0 ? itemReq.quantity() : 1;
            BigDecimal unitPrice = product.getSellPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal discountPerUnit = itemReq.discountPerUnit() != null 
                ? itemReq.discountPerUnit().setScale(2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (discountPerUnit.compareTo(BigDecimal.ZERO) < 0 || discountPerUnit.compareTo(unitPrice) > 0) {
                throw new IllegalArgumentException("El descuento por unidad para '" + product.getName() + "' no puede ser negativo ni superar su precio.");
            }

            BigDecimal finalUnitPrice = unitPrice.subtract(discountPerUnit).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineSubtotal = finalUnitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineGross = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineDiscount = discountPerUnit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);

            subtotalGross = subtotalGross.add(lineGross);
            discountItemsTotal = discountItemsTotal.add(lineDiscount);

            SaleItem item = SaleItem.builder()
                .product(product)
                .productName(product.getName())
                .productCode(product.getCode())
                .unitPrice(unitPrice)
                .discountPerUnit(discountPerUnit)
                .finalUnitPrice(finalUnitPrice)
                .quantity(qty)
                .subtotal(lineSubtotal)
                .build();

            saleItems.add(item);
        }

        BigDecimal subtotalPostItems = subtotalGross.subtract(discountItemsTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal globalDiscount = request.globalDiscountAmount() != null 
            ? request.globalDiscountAmount().setScale(2, RoundingMode.HALF_UP) 
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (globalDiscount.compareTo(BigDecimal.ZERO) < 0 || globalDiscount.compareTo(subtotalPostItems) > 0) {
            throw new IllegalArgumentException("El descuento general no puede ser negativo ni superar el total de la venta.");
        }

        BigDecimal discountTotal = discountItemsTotal.add(globalDiscount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotalPostItems.subtract(globalDiscount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountCash = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountQr = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountReceived = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal changeGiven = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (request.paymentMethod() == PaymentMethod.EFECTIVO) {
            amountCash = totalAmount;
            amountReceived = request.amountReceived() != null ? request.amountReceived().setScale(2, RoundingMode.HALF_UP) : totalAmount;
            if (amountReceived.compareTo(totalAmount) < 0) {
                throw new IllegalArgumentException("El monto recibido en efectivo no puede ser menor al total.");
            }
            changeGiven = amountReceived.subtract(totalAmount).setScale(2, RoundingMode.HALF_UP);
        } else if (request.paymentMethod() == PaymentMethod.QR) {
            amountQr = totalAmount;
            amountReceived = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            changeGiven = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (request.paymentMethod() == PaymentMethod.MIXTO) {
            BigDecimal cashPortion = request.amountCash() != null ? request.amountCash().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (cashPortion.compareTo(BigDecimal.ZERO) < 0 || cashPortion.compareTo(totalAmount) > 0) {
                throw new IllegalArgumentException("En pago mixto, la porción en efectivo debe estar entre 0 y el total.");
            }
            amountCash = cashPortion;
            amountQr = totalAmount.subtract(cashPortion).setScale(2, RoundingMode.HALF_UP);
            amountReceived = request.amountReceived() != null ? request.amountReceived().setScale(2, RoundingMode.HALF_UP) : cashPortion;
            if (amountReceived.compareTo(cashPortion) < 0) {
                throw new IllegalArgumentException("El monto recibido en efectivo no puede ser menor a la porción pactada en efectivo.");
            }
            changeGiven = amountReceived.subtract(cashPortion).setScale(2, RoundingMode.HALF_UP);
        }

        String saleNumber = formatSaleNumber(saleRepository.nextSaleNumber());

        Sale sale = Sale.builder()
            .saleNumber(saleNumber)
            .cashSession(session)
            .status(SaleStatus.COMPLETADA)
            .subtotalAmount(subtotalGross)
            .discountItemsTotal(discountItemsTotal)
            .globalDiscountAmount(globalDiscount)
            .discountTotal(discountTotal)
            .totalAmount(totalAmount)
            .paymentMethod(request.paymentMethod())
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
        BigDecimal activeExpenses = financialTransactionRepository.sumActiveCashExpensesBySessionId(session.getId());
        session.setExpectedCash(session.getOpeningAmount().add(session.getTotalSalesCash()).subtract(activeExpenses != null ? activeExpenses : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

        cashSessionRepository.save(session);

        // Generar movimientos financieros automáticos por venta
        FinancialCategory salesCategory = financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()
            .orElse(null);

        List<FinancialAccount> saleAccounts = financialAccountRepository.findSaleAccountsForUpdate();

        if (amountCash.compareTo(BigDecimal.ZERO) > 0) {
            var cashOpt = saleAccounts.stream().filter(a -> Boolean.TRUE.equals(a.getIsCashAccount())).findFirst();
            if (cashOpt.isPresent()) {
                FinancialAccount cashAccount = cashOpt.get();
                cashAccount.setCurrentBalance(cashAccount.getCurrentBalance().add(amountCash));
                financialAccountRepository.save(cashAccount);

                FinancialTransaction cashTx = FinancialTransaction.builder()
                    .type(FinancialTransactionType.VENTA_INGRESO)
                    .sourceAccount(cashAccount)
                    .category(salesCategory)
                    .amount(amountCash)
                    .transactionDate(savedSale.getCreatedAt())
                    .description("Venta #" + savedSale.getSaleNumber() + " (Efectivo)")
                    .status(FinancialTransactionStatus.ACTIVO)
                    .saleId(savedSale.getId())
                    .createdBy(username)
                    .createdAt(Instant.now())
                    .build();
                financialTransactionRepository.save(cashTx);
            }
        }

        if (amountQr.compareTo(BigDecimal.ZERO) > 0) {
            var qrOpt = saleAccounts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsQrAccount()) && a.getDeletedAt() == null)
                .findFirst();
            if (qrOpt.isPresent()) {
                FinancialAccount qrAccount = qrOpt.get();
                qrAccount.setCurrentBalance(qrAccount.getCurrentBalance().add(amountQr));
                financialAccountRepository.save(qrAccount);

                FinancialTransaction qrTx = FinancialTransaction.builder()
                    .type(FinancialTransactionType.VENTA_INGRESO)
                    .sourceAccount(qrAccount)
                    .category(salesCategory)
                    .amount(amountQr)
                    .transactionDate(savedSale.getCreatedAt())
                    .description("Venta #" + savedSale.getSaleNumber() + " (QR)")
                    .status(FinancialTransactionStatus.ACTIVO)
                    .saleId(savedSale.getId())
                    .createdBy(username)
                    .createdAt(Instant.now())
                    .build();
                financialTransactionRepository.save(qrTx);
            }
        }

        return SaleResponse.fromEntity(savedSale);
    }

    @Override
    public SaleResponse voidSale(UUID saleId, VoidSaleRequest request, String username) {
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

        sale.setStatus(SaleStatus.ANULADA);
        sale.setVoidedAt(Instant.now());
        sale.setVoidedBy(username);
        sale.setVoidReason(request.voidReason().trim());

        // Revertir acumuladores financieros de la caja abierta
        session.setTotalSalesCash(session.getTotalSalesCash().subtract(sale.getAmountCash()));
        session.setTotalSalesQr(session.getTotalSalesQr().subtract(sale.getAmountQr()));
        session.setTotalSales(session.getTotalSales().subtract(sale.getTotalAmount()));
        BigDecimal activeExpenses = financialTransactionRepository.sumActiveCashExpensesBySessionId(session.getId());
        session.setExpectedCash(session.getOpeningAmount().add(session.getTotalSalesCash()).subtract(activeExpenses != null ? activeExpenses : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));

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
                tx.setVoidReason(request.voidReason().trim());

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

    static String formatSaleNumber(long sequenceValue) {
        return String.format("VTA-%06d", sequenceValue);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getAllSales() {
        return saleRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .map(SaleResponse::fromEntity)
            .collect(Collectors.toList());
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
