package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** One consumption line; the cost comes from the product, not from the client (CON-01). */
public record ConsumptionItemRequest(
    @NotNull(message = "El producto es obligatorio.")
    UUID productId,

    @NotNull(message = "La cantidad es obligatoria.")
    BigDecimal quantity,

    // CAT-11: consumed by presentation; quantity is then the whole number of packs
    UUID presentationId
) {
    public ConsumptionItemRequest(UUID productId, BigDecimal quantity) {
        this(productId, quantity, null);
    }
}
