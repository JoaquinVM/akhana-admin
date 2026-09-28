package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Tag;
import com.akhana.akhana_admin.model.TagStatus;

import java.time.Instant;
import java.util.UUID;

public record TagResponse(
    UUID id,
    String name,
    String color,
    TagStatus status,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt
) {
    public static TagResponse fromEntity(Tag tag) {
        if (tag == null) {
            return null;
        }
        return new TagResponse(
            tag.getId(),
            tag.getName(),
            tag.getColor(),
            tag.getStatus(),
            tag.getCreatedBy(),
            tag.getCreatedAt(),
            tag.getUpdatedBy(),
            tag.getUpdatedAt(),
            tag.getDeletedBy(),
            tag.getDeletedAt()
        );
    }
}
