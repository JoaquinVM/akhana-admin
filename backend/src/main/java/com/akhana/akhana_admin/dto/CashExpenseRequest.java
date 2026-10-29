package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashExpenseRequest {

    @NotNull(message = "El monto es obligatorio.")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0.")
    private BigDecimal amount;

    @NotNull(message = "La categoría de gasto es obligatoria.")
    private UUID categoryId;

    @NotBlank(message = "La descripción es obligatoria.")
    @Size(max = 255, message = "La descripción no puede exceder 255 caracteres.")
    private String description;

    @Size(max = 500, message = "Los comentarios no pueden exceder 500 caracteres.")
    private String notes;

    private UUID cashSessionId;
}
