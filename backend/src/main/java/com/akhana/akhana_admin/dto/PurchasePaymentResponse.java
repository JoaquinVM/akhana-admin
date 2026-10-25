package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PurchasePayment;
import com.akhana.akhana_admin.model.PurchasePaymentRecordStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchasePaymentResponse(
    UUID id,
    UUID accountId,
    String accountName,
    boolean cashAccount,
    BigDecimal amount,
    Instant paymentDate,
    UUID financialTransactionId,
    PurchasePaymentRecordStatus status,
    String createdBy,
    Instant createdAt,
    String voidedBy,
    Instant voidedAt,
    String voidReason
) {
    public static PurchasePaymentResponse fromEntity(PurchasePayment payment) {
        return new PurchasePaymentResponse(
            payment.getId(),
            payment.getAccount().getId(),
            payment.getAccount().getName(),
            Boolean.TRUE.equals(payment.getAccount().getIsCashAccount()),
            payment.getAmount(),
            payment.getPaymentDate(),
            payment.getFinancialTransactionId(),
            payment.getStatus(),
            payment.getCreatedBy(),
            payment.getCreatedAt(),
            payment.getVoidedBy(),
            payment.getVoidedAt(),
            payment.getVoidReason()
        );
    }
}
