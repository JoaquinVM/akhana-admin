package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * INV-01, T-04: the stock of a product, a shortcut of the sum of its kardex rows, written only by the inventory
 * service under a row lock. startedAt null: the product waits for its first count (INV-18) or stopped controlling its
 * stock (CAT-05), and moves no stock.
 */
@Entity
@Table(name = "product_stocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStock {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, precision = 14, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO.setScale(3);

    @Column(name = "stock_value", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal value = BigDecimal.ZERO.setScale(2);

    // Weighted average cost per base unit (INV-06); null until the first entry or exit
    @Column(name = "average_cost", precision = 14, scale = 4)
    private BigDecimal averageCost;

    @Column(name = "started_at")
    private Instant startedAt;

    public boolean isStarted() {
        return startedAt != null;
    }
}
