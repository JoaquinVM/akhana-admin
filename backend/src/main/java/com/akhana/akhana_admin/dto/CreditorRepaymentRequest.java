package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Repayment to a person from Caja or another account (DEU-03). */
public record CreditorRepaymentRequest(
    @NotNull(message = "La cuenta de origen es obligatoria.")
    UUID accountId,

    @NotNull(message = "El monto es obligatorio.")
    BigDecimal amount,

    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    String comment
) {}
