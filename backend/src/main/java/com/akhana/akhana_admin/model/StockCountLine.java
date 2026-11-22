package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(name = "expected_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal expectedQuantity;

    // INV-18: the product was still to be counted when this was written: the count is its initial load
    @Column(name = "initial_load", nullable = false)
    private boolean initialLoad;

    @Column(name = "counted_by", nullable = false, length = 100)
    private String countedBy;

    @Column(name = "counted_at", nullable = false)
    private Instant countedAt;
}
