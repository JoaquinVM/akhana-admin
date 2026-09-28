package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Category;
import com.akhana.akhana_admin.model.CategoryStatus;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String description,
    String color,
    CategoryStatus status,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt
) {
    public static CategoryResponse fromEntity(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getDescription(),
            category.getColor(),
            category.getStatus(),
            category.getCreatedBy(),
            category.getCreatedAt(),
            category.getUpdatedBy(),
            category.getUpdatedAt(),
            category.getDeletedBy(),
            category.getDeletedAt()
        );
    }
}
