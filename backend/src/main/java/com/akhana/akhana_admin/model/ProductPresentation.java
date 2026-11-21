package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CAT-15: a package of a product, by unit or by kilo: how many base units it holds (Caja x12 = 12, 1/2 libra =
 * 0,227 kg). The presentations of CAT-11 are the packages that are sold. Sold without its own price it costs base
 * quantity x the product price; its cost is always base quantity x the product cost, never stored. Never deleted: a
 * removed package is deactivated because sales, consumptions and purchases keep pointing to it.
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

    @Column(name = "base_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal baseQuantity;

    // The package's own price, only when it is sold; null: base quantity x the product price
    @Column(name = "fixed_price", precision = 12, scale = 2)
    private BigDecimal fixedPrice;

    // Sold whole in the POS (CAT-15); a package only bought (Saco 25 kg) is not
    @Column(nullable = false)
    @Builder.Default
    private boolean sold = true;

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
