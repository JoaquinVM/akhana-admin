package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * History of a customer (DEU-07): its at-cost sales (on credit, or paid at once with credit 0, P-49) and
 * collections, oldest first by day of operation (CAJ-11), each with the receivable after it. Voided entries keep the previous balance.
 */
public record CustomerHistoryResponse(CustomerResponse customer, List<Entry> entries) {

    public enum EntryType { SALE, COLLECTION }

    public record Entry(
        EntryType type,
        UUID id,
        // Real date and time it was recorded
        Instant date,
        // CAJ-11: day it counts for (the day of its session or sale)
        LocalDate operationDate,
        // SALE: the part on credit (0 if paid at once); COLLECTION: the amount collected
        BigDecimal amount,
        // SALE
        UUID saleId,
        String saleNumber,
        BigDecimal saleTotal,
        // COLLECTION
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
