package com.akhana.akhana_admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequest(
    @NotNull(message = "El proveedor es obligatorio.")
    UUID supplierId,

    // Null means today (Bolivia time)
    LocalDate purchaseDate,

    @NotEmpty(message = "El pedido debe tener al menos una línea.")
    List<@Valid PurchaseItemRequest> items,

    @DecimalMin(value = "0.00", message = "El costo de envío no puede ser negativo.")
    BigDecimal shippingCost,

    // Account that paid the shipping; required when shippingCost > 0 (P-24)
    UUID shippingAccountId,

    Boolean consignment,

    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    String comment
) {}
