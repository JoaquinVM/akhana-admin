package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// COM-23: the invoice photo of a purchase; its files live with the product photos and share their copy off the VPS
@Entity
@Table(name = "purchase_photos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchasePhoto {

    @Id
    private UUID id;

    @Column(name = "purchase_id", nullable = false, updatable = false)
    private UUID purchaseId;

    @Column(name = "file_name", nullable = false, length = 64, updatable = false)
    private String fileName;

    @Column(name = "thumbnail_file_name", nullable = false, length = 64, updatable = false)
    private String thumbnailFileName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deactivated_by", length = 100)
    private String deactivatedBy;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;
}
