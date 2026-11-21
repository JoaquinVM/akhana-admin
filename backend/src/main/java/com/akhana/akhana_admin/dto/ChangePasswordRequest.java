package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.dto.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "La contraseña actual es obligatoria.")
    String currentPassword,

    @NotBlank(message = "La nueva contraseña es obligatoria.")
    @Size(min = 12, max = 72, message = "La nueva contraseña debe tener entre 12 y 72 caracteres.")
    @MaxUtf8Bytes(value = 72, message = "La nueva contraseña es demasiado larga: las letras con tilde, la ñ y los emojis cuentan como más de un carácter. Acórtela.")
    String newPassword
) {}
