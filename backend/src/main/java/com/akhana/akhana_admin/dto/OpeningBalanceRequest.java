package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// FIN-01: balance of an account at the cutover from the spreadsheets
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningBalanceRequest {

    @NotNull(message = "El saldo al corte es obligatorio.")
    @DecimalMin(value = "0.00", message = "El saldo al corte no puede ser negativo.")
    private BigDecimal amount;
}
