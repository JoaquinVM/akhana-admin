package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// CAT-10: a factory barcode of a product; removing it deactivates the row, never deletes it (RG-01)
@Entity
@Table(name = "product_barcodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 13, updatable = false)
    private String barcode;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deactivated_by", length = 100)
    private String deactivatedBy;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;
}
