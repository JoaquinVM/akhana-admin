package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Purchase;
import com.akhana.akhana_admin.model.PurchasePaymentStatus;
import com.akhana.akhana_admin.model.PurchaseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Purchase with its derived payment status and balance (COM-04). The list leaves items and payments empty;
 * the detail fills them.
 */
public record PurchaseResponse(
    UUID id,
    String purchaseNumber,
    UUID supplierId,
    String supplierName,
    LocalDate purchaseDate,
    PurchaseStatus status,
    BigDecimal itemsTotal,
    BigDecimal shippingCost,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal balance,
    PurchasePaymentStatus paymentStatus,
    boolean consignment,
    String comment,
    String shippingAccountName,
    String createdBy,
    Instant createdAt,
    List<PurchaseItemResponse> items,
    List<PurchasePaymentResponse> payments
) {
    public static PurchaseResponse summary(Purchase purchase) {
        return of(purchase, null, List.of(), List.of());
    }

    public static PurchaseResponse detail(Purchase purchase, String shippingAccountName) {
        return of(
            purchase,
            shippingAccountName,
            purchase.getItems().stream().map(PurchaseItemResponse::fromEntity).toList(),
            purchase.getPayments().stream().map(PurchasePaymentResponse::fromEntity).toList()
        );
    }

    private static PurchaseResponse of(Purchase purchase, String shippingAccountName,
                                       List<PurchaseItemResponse> items, List<PurchasePaymentResponse> payments) {
        return new PurchaseResponse(
            purchase.getId(),
            purchase.getPurchaseNumber(),
            purchase.getSupplier().getId(),
            purchase.getSupplier().getName(),
            purchase.getPurchaseDate(),
            purchase.getStatus(),
            purchase.getItemsTotal(),
            purchase.getShippingCost(),
            purchase.getTotalAmount(),
            purchase.getPaidAmount(),
            purchase.getBalance(),
            purchase.getPaymentStatus(),
            Boolean.TRUE.equals(purchase.getConsignment()),
            purchase.getComment(),
            shippingAccountName,
            purchase.getCreatedBy(),
            purchase.getCreatedAt(),
            items,
            payments
        );
    }
}
