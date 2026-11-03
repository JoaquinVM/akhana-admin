package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.DeliveryStatus;
import com.akhana.akhana_admin.model.DeliveryType;
import com.akhana.akhana_admin.model.FinancialAccount;
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
    UUID shippingAccountId,
    String shippingAccountName,
    DeliveryType deliveryType,
    DeliveryStatus deliveryStatus,
    LocalDate deliveryDate,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String voidedBy,
    Instant voidedAt,
    String voidReason,
    List<PurchaseItemResponse> items,
    List<PurchasePaymentResponse> payments,
    // COM-12: edits, the most recent first
    List<PurchaseRevisionResponse> revisions,
    // FIN-11: migrated from the spreadsheet; it only accepts new payments
    boolean historical,
    // COM-22: its costs are the system's until the real ones are registered, and its amount is an estimate
    boolean costToConfirm,
    // COM-23: the active invoice photo; only in the detail
    UUID photoId
) {
    public static PurchaseResponse summary(Purchase purchase) {
        return of(purchase, null, List.of(), List.of(), List.of(), null);
    }

    public static PurchaseResponse detail(Purchase purchase, FinancialAccount shippingAccount) {
        return detail(purchase, shippingAccount, null);
    }

    public static PurchaseResponse detail(Purchase purchase, FinancialAccount shippingAccount, UUID photoId) {
        return of(
            purchase,
            shippingAccount,
            // Lines removed while editing are only in the revisions (COM-11)
            purchase.getItems().stream().filter(item -> !item.isRemoved()).map(PurchaseItemResponse::fromEntity).toList(),
            purchase.getPayments().stream().map(PurchasePaymentResponse::fromEntity).toList(),
            purchase.getRevisions().stream().map(PurchaseRevisionResponse::fromEntity).toList(),
            photoId
        );
    }

    private static PurchaseResponse of(Purchase purchase, FinancialAccount shippingAccount,
                                       List<PurchaseItemResponse> items, List<PurchasePaymentResponse> payments,
                                       List<PurchaseRevisionResponse> revisions, UUID photoId) {
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
            shippingAccount != null ? shippingAccount.getId() : null,
            shippingAccount != null ? shippingAccount.getName() : null,
            purchase.getDeliveryType(),
            purchase.getDeliveryStatus(),
            purchase.getDeliveryDate(),
            purchase.getCreatedBy(),
            purchase.getCreatedAt(),
            purchase.getUpdatedBy(),
            purchase.getUpdatedAt(),
            purchase.getVoidedBy(),
            purchase.getVoidedAt(),
            purchase.getVoidReason(),
            items,
            payments,
            revisions,
            purchase.isHistorical(),
            Boolean.TRUE.equals(purchase.getCostToConfirm()),
            photoId
        );
    }
}
