package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.CreditorRepaymentConcept;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * History of a person (DEU-04): purchase payments made by them, sales of their products (POS-13) and repayments to
 * them, oldest first, each with the debt after it. Voided entries keep the previous balance.
 */
public record CreditorHistoryResponse(CreditorResponse creditor, List<Entry> entries) {

    public enum EntryType { PAYMENT, SALE, REPAYMENT }

    public record Entry(
        EntryType type,
        UUID id,
        Instant date,
        BigDecimal amount,
        // PAYMENT
        UUID purchaseId,
        String purchaseNumber,
        String supplierName,
        // SALE: amount is the sum of the person's lines in that sale
        UUID saleId,
        String saleNumber,
        // REPAYMENT
        UUID accountId,
        String accountName,
        String comment,
        CreditorRepaymentConcept concept,
        boolean voided,
        String voidedBy,
        Instant voidedAt,
        String voidReason,
        BigDecimal balanceAfter
    ) {}
}
