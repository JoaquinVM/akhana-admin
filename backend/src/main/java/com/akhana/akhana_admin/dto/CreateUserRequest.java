package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record CreateUserRequest(
    @NotBlank(message = "El nombre completo es obligatorio.")
    @Size(min = 2, max = 150, message = "El nombre completo debe tener entre 2 y 150 caracteres.")
    String fullName,

    @NotBlank(message = "El nombre de usuario es obligatorio.")
    @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres.")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "El nombre de usuario solo puede contener letras, números, guiones y puntos.")
    String username,

    @NotBlank(message = "La contraseña es obligatoria.")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres.")
    String password,

    @NotNull(message = "El rol es obligatorio.")
    Role role
) {}
