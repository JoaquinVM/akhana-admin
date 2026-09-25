package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Role;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    Role role
) {}
