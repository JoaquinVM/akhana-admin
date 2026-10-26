package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * History of a customer (DEU-07): sales with a part on credit and collections, oldest first, each with the
 * receivable after it. Voided entries keep the previous balance.
 */
public record CustomerHistoryResponse(CustomerResponse customer, List<Entry> entries) {

    public enum EntryType { SALE, COLLECTION }

    public record Entry(
        EntryType type,
        UUID id,
        Instant date,
        // SALE: the part on credit; COLLECTION: the amount collected
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
