package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "product_code", length = 50)
    private String productCode;

    // Up to 3 decimals for products sold by weight (CAT-08)
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    // Product cost when the purchase was registered (COM-03)
    @Column(name = "previous_unit_cost", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal previousUnitCost;

    @Column(name = "cost_updated", nullable = false)
    @Builder.Default
    private Boolean costUpdated = false;

    // COM-11: removed while editing the purchase (it did not arrive); it stays out of the totals and the detail
    @Column(name = "removed_by", length = 100)
    private String removedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    public boolean isRemoved() {
        return removedAt != null;
    }
}
