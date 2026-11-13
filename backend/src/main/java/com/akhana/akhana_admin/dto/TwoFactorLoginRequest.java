package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorLoginRequest(
    @NotBlank(message = "El ingreso venció. Vuelva a ingresar su usuario y contraseña.")
    String challengeId,

    @NotBlank(message = "Ingrese el código de 6 dígitos.")
    String code
) {}
