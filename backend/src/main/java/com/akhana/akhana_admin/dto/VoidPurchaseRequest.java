package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason to void a purchase or one of its payments (COM-09, RG-03). */
public record VoidPurchaseRequest(
    @NotBlank(message = "El motivo de anulación es obligatorio.")
    @Size(max = 500, message = "El motivo de anulación no puede exceder 500 caracteres.")
    String voidReason
) {}
