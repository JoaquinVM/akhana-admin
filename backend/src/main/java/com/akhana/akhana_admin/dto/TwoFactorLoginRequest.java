package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorLoginRequest(
    @NotBlank(message = "El ingreso venció. Vuelve a ingresar tu usuario y contraseña.")
    String challengeId,

    @NotBlank(message = "Ingresa el código de 6 dígitos.")
    String code,

    // 098: the one-time activation code, only when activating the second factor asks for it
    String activationCode
) {
    public TwoFactorLoginRequest(String challengeId, String code) {
        this(challengeId, code, null);
    }
}
