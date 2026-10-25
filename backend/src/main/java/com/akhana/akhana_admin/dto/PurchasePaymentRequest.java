package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Purchase payment from an account or made by a person (COM-05, DEU-02): exactly one of the two. */
public record PurchasePaymentRequest(
    UUID accountId,

    UUID creditorId,

    @NotNull(message = "El monto es obligatorio.")
    BigDecimal amount,

    @Size(max = 500, message = "La nota no puede exceder 500 caracteres.")
    String notes
) {}
