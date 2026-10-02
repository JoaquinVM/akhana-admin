package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CashCutDto(
    @NotNull(message = "La denominación es obligatoria.")
    @DecimalMin(value = "0.01", message = "La denominación debe ser mayor a 0.")
    BigDecimal denomination,

    @NotNull(message = "La cantidad en caja es obligatoria.")
    @Min(value = 0, message = "La cantidad en caja no puede ser negativa.")
    Integer cashQuantity,

    @NotNull(message = "La cantidad en reserva es obligatoria.")
    @Min(value = 0, message = "La cantidad en reserva no puede ser negativa.")
    Integer reserveQuantity,

    BigDecimal subtotal
) {}
