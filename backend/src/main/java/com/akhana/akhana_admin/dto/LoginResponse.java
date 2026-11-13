package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;

import java.util.Set;
import java.util.UUID;

/** Either a session (token) or, for an administrator, the second factor still to enter (twoFactor, USR-08). */
public record LoginResponse(
    String token,
    UUID id,
    String username,
    String fullName,
    Role role,
    Set<String> permissions,
    boolean mustChangePassword,
    String message,
    TwoFactorChallengeResponse twoFactor
) {
    public LoginResponse(String token, UUID id, String username, Role role, String message) {
        this(token, id, username, username, role, Set.of(), false, message, null);
    }

    public static LoginResponse secondFactor(String username, String message, TwoFactorChallengeResponse twoFactor) {
        return new LoginResponse(null, null, username, null, null, Set.of(), false, message, twoFactor);
    }
}
