package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;

import java.util.UUID;

public record LoginResponse(
    String token,
    UUID id,
    String username,
    Role role,
    String message
) {
    public LoginResponse(UUID id, String username, Role role, String message) {
        this(null, id, username, role, message);
    }
}
