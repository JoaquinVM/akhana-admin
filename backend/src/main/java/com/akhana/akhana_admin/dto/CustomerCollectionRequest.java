package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Collection from a customer into Caja or the QR account (DEU-06). */
public record CustomerCollectionRequest(
    @NotNull(message = "La cuenta de destino es obligatoria.")
    UUID accountId,

    @NotNull(message = "El monto es obligatorio.")
    BigDecimal amount,

    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    String comment
) {}
