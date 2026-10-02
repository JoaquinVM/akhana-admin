package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemRequest(
    @NotNull(message = "El ID del producto es obligatorio")
    UUID productId,

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima debe ser 1")
    Integer quantity,

    @DecimalMin(value = "0.00", message = "El descuento unitario no puede ser negativo")
    BigDecimal discountPerUnit
) {}
