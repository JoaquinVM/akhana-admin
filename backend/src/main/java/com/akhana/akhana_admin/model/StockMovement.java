package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * INV-02, INV-03: a row of the valued kardex. Quantity and value are signed; the balance after it is the sum of the
 * rows of its product (T-04). Never edited nor deleted: every correction is another row by the difference, with the
 * server's date (INV-08).
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private StockMovementType type;

    @Column(nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal quantity;

    // Per base unit; null on the rows that only change the value
    @Column(name = "unit_cost", precision = 14, scale = 4, updatable = false)
    private BigDecimal unitCost;

    @Column(name = "stock_value", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal value;

    // INV-07: what went to the cost of sales because no stock was left
    @Column(name = "expensed_value", nullable = false, precision = 14, scale = 2, updatable = false)
    @Builder.Default
    private BigDecimal expensedValue = BigDecimal.ZERO.setScale(2);

    @Column(name = "balance_quantity", nullable = false, precision = 14, scale = 3, updatable = false)
    private BigDecimal balanceQuantity;

    @Column(name = "balance_value", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal balanceValue;

    @Column(name = "balance_average_cost", precision = 14, scale = 4, updatable = false)
    private BigDecimal balanceAverageCost;

    // The document, as the screens name it: VTA-000123, PED-000045, CON-000012, Conteo #3
    @Column(length = 60, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private StockAdjustmentReason reason;

    @Column(length = 255, updatable = false)
    private String comment;

    @Column(name = "sale_item_id", updatable = false)
    private UUID saleItemId;

    @Column(name = "consumption_item_id", updatable = false)
    private UUID consumptionItemId;

    @Column(name = "purchase_item_id", updatable = false)
    private UUID purchaseItemId;

    @Column(name = "stock_count_line_id", updatable = false)
    private UUID stockCountLineId;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
