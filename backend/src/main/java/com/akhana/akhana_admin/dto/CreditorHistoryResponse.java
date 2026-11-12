package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.CreditorRepaymentConcept;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * History of a person (DEU-04): purchase payments and shipping paid by them (DEU-02), sales of their products
 * (POS-13) and repayments to them, oldest first, each with the debt after it. Voided entries keep the previous balance.
 * Each debt says what is still owed of it and each repayment what it paid (DEU-03).
 */
public record CreditorHistoryResponse(CreditorResponse creditor, List<Entry> entries) {

    public enum EntryType { PAYMENT, SHIPPING, SALE, REPAYMENT }

    public record Entry(
        EntryType type,
        UUID id,
        // Real date and time it was recorded
        Instant date,
        // CAJ-11: day it counts for (the day of its session or sale)
        LocalDate operationDate,
        BigDecimal amount,
        // PAYMENT and SHIPPING
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
        // DEU-03 (062), PAYMENT, SHIPPING and SALE not voided: what is still owed of it; 0 once paid
        BigDecimal pendingAmount,
        // Day of the repayment that finished paying it (CAJ-11)
        LocalDate paidOn,
        // REPAYMENT: what it paid, oldest first (PED-000015, «Envío PED-000020», VTA-000260)
        List<String> paidItems,
        BigDecimal balanceAfter
    ) {}
}
