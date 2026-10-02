package com.akhana.akhana_admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuickProductGroupResponse(
    UUID id,
    String name,
    Integer displayOrder,
    Instant createdAt,
    Instant updatedAt,
    List<ProductResponse> products
) {}
