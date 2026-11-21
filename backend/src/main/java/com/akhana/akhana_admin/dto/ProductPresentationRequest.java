package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One package in the product form (CAT-15, the presentations of CAT-11). Without id it is new; active false
 * deactivates it. Sold null keeps the package sold, as every presentation was; barcode null or blank: none.
 * The rules (name, base quantity, price, barcode, duplicates) are checked by the service so the messages match the spec.
 */
public record ProductPresentationRequest(
    UUID id,
    String name,
    BigDecimal baseQuantity,
    BigDecimal fixedPrice,
    Boolean active,
    Boolean sold,
    String barcode
) {
    public ProductPresentationRequest(UUID id, String name, BigDecimal baseQuantity, BigDecimal fixedPrice, Boolean active) {
        this(id, name, baseQuantity, fixedPrice, active, null, null);
    }
}
