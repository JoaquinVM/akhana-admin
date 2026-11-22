package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.SaleUnit;
import com.akhana.akhana_admin.model.StockCountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * INV-05: a count and what to count in it. Blind: while it is open no product carries its stock nor what is expected,
 * only what was counted.
 */
public record StockCountResponse(
    UUID id,
    long number,
    UUID categoryId,
    String categoryName,
    StockCountStatus status,
    String createdBy,
    Instant createdAt,
    String closedBy,
    Instant closedAt,
    int countedProducts,
    List<CountProduct> products
) {
    /** A product to count: status CONTROLLED or TO_COUNT (INV-18), its packages and barcodes, and what was counted. */
    public record CountProduct(
        UUID productId,
        String code,
        String name,
        SaleUnit saleUnit,
        String status,
        List<String> barcodes,
        List<CountPackage> packages,
        BigDecimal countedQuantity,
        String breakdown,
        String countedBy,
        Instant countedAt
    ) {
    }

    public record CountPackage(UUID id, String name, BigDecimal baseQuantity, String barcode) {
    }
}
