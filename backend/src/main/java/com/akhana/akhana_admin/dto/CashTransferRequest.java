package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.CashTransferDirection;
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
public class CashTransferRequest {

    @NotNull(message = "Indica si es un ingreso o un retiro de caja.")
    private CashTransferDirection direction;

    @NotNull(message = "La cuenta es obligatoria.")
    private UUID accountId;

    @NotNull(message = "El monto es obligatorio.")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0.")
    private BigDecimal amount;

    @NotBlank(message = "El comentario es obligatorio.")
    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    private String comment;
}
