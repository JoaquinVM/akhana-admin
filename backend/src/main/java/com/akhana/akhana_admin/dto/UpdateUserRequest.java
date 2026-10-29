package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record UpdateUserRequest(
    @NotBlank(message = "El nombre completo es obligatorio.")
    @Size(min = 2, max = 150, message = "El nombre completo debe tener entre 2 y 150 caracteres.")
    String fullName,

    @NotNull(message = "El rol es obligatorio.")
    Role role
) {}
