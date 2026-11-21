package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** COM-10: a box product reviewed once, converted into a package of its base product or discarded; never updated. */
@Entity
@Table(name = "box_product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoxProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "box_product_id", nullable = false, updatable = false, unique = true)
    private UUID boxProductId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private BoxProductDecision decision;

    @Column(name = "base_product_id", updatable = false)
    private UUID baseProductId;

    @Column(name = "presentation_id", updatable = false)
    private UUID presentationId;

    @Column(name = "decided_by", nullable = false, length = 100, updatable = false)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;
}
