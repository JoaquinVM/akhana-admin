package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "La contraseña actual es obligatoria.")
    String currentPassword,

    @NotBlank(message = "La nueva contraseña es obligatoria.")
    @Size(min = 12, max = 100, message = "La nueva contraseña debe tener entre 12 y 100 caracteres.")
    String newPassword
) {}
