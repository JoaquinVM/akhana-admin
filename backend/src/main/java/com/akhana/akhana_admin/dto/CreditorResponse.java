package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Creditor;
import com.akhana.akhana_admin.model.CreditorStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Person with the debt computed from their active payments and repayments (DEU-01, DEU-04). */
public record CreditorResponse(
    UUID id,
    String name,
    String phone,
    String description,
    CreditorStatus status,
    BigDecimal debt,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt
) {
    public static CreditorResponse fromEntity(Creditor creditor, BigDecimal debt) {
        return new CreditorResponse(
            creditor.getId(),
            creditor.getName(),
            creditor.getPhone(),
            creditor.getDescription(),
            creditor.getStatus(),
            debt,
            creditor.getCreatedBy(),
            creditor.getCreatedAt(),
            creditor.getUpdatedBy(),
            creditor.getUpdatedAt(),
            creditor.getDeletedBy(),
            creditor.getDeletedAt()
        );
    }
}
