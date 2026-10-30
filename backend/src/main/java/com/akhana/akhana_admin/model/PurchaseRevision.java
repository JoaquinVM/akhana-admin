package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Audit row of a purchase edit (COM-12): who, when, why and a summary of what changed; never updated. */
@Entity
@Table(name = "purchase_revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String changes;

    @Column(name = "revised_by", nullable = false, length = 100, updatable = false)
    private String revisedBy;

    @Column(name = "revised_at", nullable = false, updatable = false)
    private Instant revisedAt;
}
