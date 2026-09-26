package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.SupplierStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
    @NotBlank(message = "El nombre del proveedor es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    String name,

    @NotBlank(message = "El código del proveedor es obligatorio")
    @Size(max = 50, message = "El código no puede exceder 50 caracteres")
    String code,

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    String description,

    @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres")
    String phone,

    SupplierStatus status
) {}
