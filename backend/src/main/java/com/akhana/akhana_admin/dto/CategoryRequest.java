package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    String name,

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    String description,

    @NotBlank(message = "El color de la categoría es obligatorio")
    @Size(max = 50, message = "El color no puede exceder 50 caracteres")
    String color
) {}
