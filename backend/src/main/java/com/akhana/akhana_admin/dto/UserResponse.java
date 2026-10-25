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
    String deletedBy
) {}
