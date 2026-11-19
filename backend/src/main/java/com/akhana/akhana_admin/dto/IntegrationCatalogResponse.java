package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * INT-01: the catalog the WhatsApp bot may tell customers. Only what a customer may know: never the cost, the
 * profit, the supplier, tags, barcodes or audit users.
 */
public record IntegrationCatalogResponse(
    Instant generatedAt,
    String currency,
    List<Item> products
) {
    /**
     * An active product. For UNIT, price is the price of one unit; for KILOGRAM, price is per kilo and
     * presentations carry the price of each fixed weight (CAT-11).
     */
    public record Item(
        String code,
        String name,
        String category,
        String description,
        String saleUnit,
        BigDecimal price,
        List<Presentation> presentations,
        Instant updatedAt
    ) {}

    /** An active presentation with its price: the fixed one, or weight x price per kilo. */
    public record Presentation(String name, BigDecimal weightKg, BigDecimal price) {}
}
