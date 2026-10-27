package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Creditor;
import com.akhana.akhana_admin.model.FinancialAccount;
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
    // Person who paid (DEU-02); null for account payments
    UUID creditorId,
    String creditorName,
    BigDecimal amount,
    Instant paymentDate,
    UUID financialTransactionId,
    PurchasePaymentRecordStatus status,
    String createdBy,
    Instant createdAt,
    String voidedBy,
    Instant voidedAt,
    String voidReason,
    // FIN-11: migrated without a movement (P-43)
    boolean historical
) {
    public static PurchasePaymentResponse fromEntity(PurchasePayment payment) {
        FinancialAccount account = payment.getAccount();
        Creditor creditor = payment.getCreditor();
        return new PurchasePaymentResponse(
            payment.getId(),
            account != null ? account.getId() : null,
            account != null ? account.getName() : null,
            account != null && Boolean.TRUE.equals(account.getIsCashAccount()),
            creditor != null ? creditor.getId() : null,
            creditor != null ? creditor.getName() : null,
            payment.getAmount(),
            payment.getPaymentDate(),
            payment.getFinancialTransactionId(),
            payment.getStatus(),
            payment.getCreatedBy(),
            payment.getCreatedAt(),
            payment.getVoidedBy(),
            payment.getVoidedAt(),
            payment.getVoidReason(),
            payment.isHistorical()
        );
    }
}
