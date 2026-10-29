package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidSaleRequest(
    @NotBlank(message = "El motivo de anulación es obligatorio.")
    @Size(max = 500, message = "El motivo de anulación no puede exceder 500 caracteres.")
    String voidReason
) {}
