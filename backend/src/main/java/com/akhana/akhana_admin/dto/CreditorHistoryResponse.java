package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * History of a person (DEU-04): purchase payments made by them and repayments to them, oldest first, each with
 * the debt after it. Voided entries keep the previous balance.
 */
public record CreditorHistoryResponse(CreditorResponse creditor, List<Entry> entries) {

    public enum EntryType { PAYMENT, REPAYMENT }

    public record Entry(
        EntryType type,
        UUID id,
        Instant date,
        BigDecimal amount,
        // PAYMENT
        UUID purchaseId,
        String purchaseNumber,
        String supplierName,
        // REPAYMENT
        UUID accountId,
        String accountName,
        String comment,
        boolean voided,
        String voidedBy,
        Instant voidedAt,
        String voidReason,
        BigDecimal balanceAfter
    ) {}
}
