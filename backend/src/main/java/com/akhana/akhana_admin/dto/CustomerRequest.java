package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.CustomerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
    @NotBlank(message = "El nombre del cliente es obligatorio.")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres.")
    String name,

    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres.")
    String phone,

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres.")
    String description,

    // Null keeps the current status; ELIMINADO only through delete
    CustomerStatus status
) {}
