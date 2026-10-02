package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderItemsRequest(
    @NotEmpty(message = "La lista ordenada de IDs de productos es obligatoria")
    List<UUID> productIds
) {}
