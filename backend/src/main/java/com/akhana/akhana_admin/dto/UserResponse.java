package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String fullName,
    String username,
    Role role,
    boolean active,
    Set<String> permissions,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    Instant deletedAt,
    String deletedBy,
    boolean twoFactorEnabled,
    // 098: the second factor waits for the activation code handed by an administrator
    boolean activationPending,
    // 098: the one-time activation code, only in the answer that generated it; never stored nor sent again
    String activationCode
) {
    public UserResponse withActivationCode(String code) {
        return new UserResponse(id, fullName, username, role, active, permissions, createdAt, createdBy, updatedAt,
            updatedBy, deletedAt, deletedBy, twoFactorEnabled, activationPending, code);
    }
}
