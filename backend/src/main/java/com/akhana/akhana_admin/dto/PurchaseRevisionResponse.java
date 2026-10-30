package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PurchaseRevision;

import java.time.Instant;
import java.util.UUID;

/** One edit of a purchase (COM-12). */
public record PurchaseRevisionResponse(
    UUID id,
    String reason,
    String changes,
    String revisedBy,
    Instant revisedAt
) {
    public static PurchaseRevisionResponse fromEntity(PurchaseRevision revision) {
        return new PurchaseRevisionResponse(revision.getId(), revision.getReason(), revision.getChanges(),
            revision.getRevisedBy(), revision.getRevisedAt());
    }
}
