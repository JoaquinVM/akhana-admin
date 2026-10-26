package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CAT-11: a fixed weight of a product sold by weight (1/2 libra = 0,227 kg). Without a fixed price it costs
 * weight x the product price per kilo; its cost is always weight x the product cost per kilo, never stored.
 * Never deleted: a removed presentation is deactivated because sales and consumptions keep pointing to it.
 */
@Entity
@Table(name = "product_presentations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPresentation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "fixed_price", precision = 12, scale = 2)
    private BigDecimal fixedPrice;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
