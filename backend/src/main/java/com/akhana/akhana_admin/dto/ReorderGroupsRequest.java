package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderGroupsRequest(
    @NotEmpty(message = "La lista ordenada de IDs de grupos es obligatoria")
    List<UUID> groupIds
) {}
