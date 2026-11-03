package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.ProductWebPublicationChange;

import java.time.Instant;

// CAT-13: one line of the history shown in the product audit
public record ProductWebPublicationChangeResponse(boolean published, String changedBy, Instant changedAt) {

    public static ProductWebPublicationChangeResponse fromEntity(ProductWebPublicationChange change) {
        return new ProductWebPublicationChangeResponse(change.isPublished(), change.getChangedBy(), change.getChangedAt());
    }
}
