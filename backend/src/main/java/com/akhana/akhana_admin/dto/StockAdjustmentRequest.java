package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.StockAdjustmentReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** INV-02: an adjustment by hand: merma, rotura and vencimiento take out, sobrante adds; quantity in base units. */
public record StockAdjustmentRequest(
    @NotNull(message = "Elige el motivo del ajuste.") StockAdjustmentReason reason,
    @NotNull(message = "Escribe la cantidad del ajuste.") BigDecimal quantity,
    @Size(max = 255, message = "El comentario no debe superar los 255 caracteres.") String comment
) {
}
