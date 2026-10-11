package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAccountRequest {

    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String name;

    @Size(max = 50, message = "El color no puede exceder 50 caracteres")
    private String color;

    private BigDecimal initialBalance;

    private Boolean isQrAccount;
}
