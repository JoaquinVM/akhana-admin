package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OpenCashRequest(
    @NotNull(message = "El monto inicial es obligatorio.")
    @DecimalMin(value = "0.00", message = "El monto inicial debe ser mayor o igual a 0.")
    BigDecimal openingAmount,

    @Size(max = 500, message = "El comentario no debe superar los 500 caracteres.")
    String openingComment
) {}
