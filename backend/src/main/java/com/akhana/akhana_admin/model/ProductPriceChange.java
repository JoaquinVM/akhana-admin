package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Audit row of a product cost or sell price change (CAT-07, COM-03); never updated. */
@Entity
@Table(name = "product_price_changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPriceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "previous_buy_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal previousBuyPrice;

    @Column(name = "new_buy_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal newBuyPrice;

    @Column(name = "previous_sell_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal previousSellPrice;

    @Column(name = "new_sell_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal newSellPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PriceChangeSource source;

    @Column(name = "purchase_id", updatable = false)
    private UUID purchaseId;

    @Column(name = "changed_by", nullable = false, length = 100, updatable = false)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
