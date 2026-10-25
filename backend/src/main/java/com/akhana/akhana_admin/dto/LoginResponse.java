package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;

import java.util.Set;
import java.util.UUID;

public record LoginResponse(
    String token,
    UUID id,
    String username,
    String fullName,
    Role role,
    Set<String> permissions,
    String message
) {
    public LoginResponse(String token, UUID id, String username, Role role, String message) {
        this(token, id, username, username, role, Set.of(), message);
    }

    public LoginResponse(UUID id, String username, Role role, String message) {
        this(null, id, username, username, role, Set.of(), message);
    }
}
