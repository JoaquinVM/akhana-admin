package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * INV-05: what was counted of one product, in base units, with the packages and loose units as written. The expected
 * quantity is the stock at the moment it was written, never shown while counting.
 */
@Entity
@Table(name = "stock_count_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_id", nullable = false, updatable = false)
    private StockCount stockCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "counted_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal countedQuantity;

    @Column(nullable = false, length = 300)
    private String breakdown;

    // INV-05: the whole packs of each package and the loose base units as saved, so counting again adds to them
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counted_packages", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<UUID, BigDecimal> countedPackages = new HashMap<>();

    @Column(name = "counted_loose", nullable = false, precision = 14, scale = 3)
    @Builder.Default
    private BigDecimal countedLoose = BigDecimal.ZERO;

    @Column(name = "expected_quantity", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal expectedQuantity;

    // INV-18: the product was still to be counted when this was written: the count is its initial load
    @Column(name = "initial_load", nullable = false, updatable = false)
    private boolean initialLoad;

    @Column(name = "counted_by", nullable = false, length = 100, updatable = false)
    private String countedBy;

    // The first pass: what is expected and the sales and deliveries to account for are from this moment
    @Column(name = "counted_at", nullable = false, updatable = false)
    private Instant countedAt;

    // INV-05: who saved it again, adding to it, and when
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
