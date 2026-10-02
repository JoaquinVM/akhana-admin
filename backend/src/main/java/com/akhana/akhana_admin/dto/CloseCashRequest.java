package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CloseCashRequest(
    @NotNull(message = "El monto de cierre es obligatorio.")
    @DecimalMin(value = "0.00", message = "El monto de cierre debe ser mayor o igual a 0.")
    BigDecimal closingAmount,

    @Size(max = 500, message = "El comentario no debe superar los 500 caracteres.")
    String closingComment,

    List<CashCutDto> cuts
) {}
