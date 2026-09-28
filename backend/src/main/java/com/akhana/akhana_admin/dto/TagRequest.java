package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
    @NotBlank(message = "El nombre de la etiqueta es obligatorio")
    @Size(max = 150, message = "El nombre no puede exceder 150 caracteres")
    String name,

    @NotBlank(message = "El color de la etiqueta es obligatorio")
    @Size(max = 50, message = "El color no puede exceder 50 caracteres")
    String color
) {}
