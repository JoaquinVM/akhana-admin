package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** One consumption line; the cost comes from the product, not from the client (CON-01). */
public record ConsumptionItemRequest(
    @NotNull(message = "El producto es obligatorio.")
    UUID productId,

    @NotNull(message = "La cantidad es obligatoria.")
    BigDecimal quantity
) {}
