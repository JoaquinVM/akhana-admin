package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidSaleRequest(
    @NotBlank(message = "El motivo de anulación es obligatorio")
    @Size(min = 5, max = 500, message = "El motivo de anulación debe tener entre 5 y 500 caracteres")
    String voidReason
) {}
