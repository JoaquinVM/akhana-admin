package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One presentation in the product form (CAT-11). Without id it is new; active false deactivates it.
 * The rules (name, weight, fixed price, duplicates) are checked by the service so the messages match the spec.
 */
public record ProductPresentationRequest(
    UUID id,
    String name,
    BigDecimal weightKg,
    BigDecimal fixedPrice,
    Boolean active
) {}
