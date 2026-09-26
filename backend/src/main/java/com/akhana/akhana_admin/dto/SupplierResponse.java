package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Supplier;
import com.akhana.akhana_admin.model.SupplierStatus;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
    UUID id,
    String name,
    String code,
    String description,
    String phone,
    SupplierStatus status,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt
) {
    public static SupplierResponse fromEntity(Supplier supplier) {
        if (supplier == null) {
            return null;
        }
        return new SupplierResponse(
            supplier.getId(),
            supplier.getName(),
            supplier.getCode(),
            supplier.getDescription(),
            supplier.getPhone(),
            supplier.getStatus(),
            supplier.getCreatedBy(),
            supplier.getCreatedAt(),
            supplier.getUpdatedBy(),
            supplier.getUpdatedAt(),
            supplier.getDeletedBy(),
            supplier.getDeletedAt()
        );
    }
}
