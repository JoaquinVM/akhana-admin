package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.FinancialTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialTransactionRequest {

    @NotNull(message = "El tipo de transacción es obligatorio")
    private FinancialTransactionType type;

    @NotNull(message = "La cuenta origen es obligatoria")
    private UUID sourceAccountId;

    private UUID destinationAccountId;

    private UUID categoryId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal amount;

    private Instant transactionDate;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
    private String description;

    @Size(max = 500, message = "Los comentarios no pueden exceder 500 caracteres")
    private String notes;

    private UUID cashSessionId;
}
