package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;

import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    String fullName,
    Role role,
    Set<String> permissions
) {
    public UserProfileResponse(UUID id, String username, Role role) {
        this(id, username, username, role, Set.of());
    }
}
