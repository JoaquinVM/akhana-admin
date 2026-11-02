package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// CAJ-14
public record ReopenCashRequest(
    @NotBlank(message = "El motivo es obligatorio.")
    @Size(max = 500, message = "El motivo no debe superar los 500 caracteres.")
    String reason
) {}
