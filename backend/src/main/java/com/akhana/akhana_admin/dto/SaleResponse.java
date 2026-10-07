package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.Sale;
import com.akhana.akhana_admin.model.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record SaleResponse(
    UUID id,
    String saleNumber,
    UUID cashSessionId,
    Long sessionNumber,
    SaleStatus status,
    BigDecimal subtotalAmount,
    BigDecimal discountItemsTotal,
    BigDecimal globalDiscountAmount,
    BigDecimal discountTotal,
    BigDecimal totalAmount,
    PaymentMethod paymentMethod,
    BigDecimal amountCash,
    BigDecimal amountQr,
    BigDecimal amountReceived,
    BigDecimal changeGiven,
    String description,
    String createdBy,
    Instant createdAt,
    Instant voidedAt,
    String voidedBy,
    String voidReason,
    List<SaleItemResponse> items
) {
    public SaleResponse(
        UUID id,
        String saleNumber,
        UUID cashSessionId,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        String description,
        String createdBy,
        Instant createdAt
    ) {
        this(
            id,
            saleNumber,
            cashSessionId,
            null,
            SaleStatus.COMPLETADA,
            totalAmount,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            totalAmount,
            paymentMethod,
            paymentMethod == PaymentMethod.EFECTIVO ? totalAmount : BigDecimal.ZERO,
            paymentMethod == PaymentMethod.QR ? totalAmount : BigDecimal.ZERO,
            totalAmount,
            BigDecimal.ZERO,
            description,
            createdBy,
            createdAt,
            null,
            null,
            null,
            Collections.emptyList()
        );
    }

    public static SaleResponse fromEntity(Sale sale) {
        List<SaleItemResponse> itemResponses = sale.getItems() != null
            ? sale.getItems().stream()
                .map(item -> new SaleItemResponse(
                    item.getId(),
                    item.getProduct() != null ? item.getProduct().getId() : null,
                    item.getProductName(),
                    item.getProductCode(),
                    item.getUnitPrice(),
                    item.getDiscountPerUnit(),
                    item.getFinalUnitPrice(),
                    item.getQuantity(),
                    item.getSubtotal()
                ))
                .collect(Collectors.toList())
            : Collections.emptyList();

        Long sessionNumber = sale.getCashSession() != null ? sale.getCashSession().getSessionNumber() : null;

        return new SaleResponse(
            sale.getId(),
            sale.getSaleNumber(),
            sale.getCashSession() != null ? sale.getCashSession().getId() : null,
            sessionNumber,
            sale.getStatus(),
            sale.getSubtotalAmount(),
            sale.getDiscountItemsTotal(),
            sale.getGlobalDiscountAmount(),
            sale.getDiscountTotal(),
            sale.getTotalAmount(),
            sale.getPaymentMethod(),
            sale.getAmountCash(),
            sale.getAmountQr(),
            sale.getAmountReceived(),
            sale.getChangeGiven(),
            sale.getDescription(),
            sale.getCreatedBy(),
            sale.getCreatedAt(),
            sale.getVoidedAt(),
            sale.getVoidedBy(),
            sale.getVoidReason(),
            itemResponses
        );
    }
}
