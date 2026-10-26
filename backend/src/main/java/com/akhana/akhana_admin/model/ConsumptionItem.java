package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "consumption_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumption_id", nullable = false)
    private Consumption consumption;

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

    // Copy of the product cost when registered: later cost changes do not alter it (like POS-05)
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    // CAT-11: a line sold by presentation keeps a copy of it; quantity is then the number of packs and weightKg
    // the kilos (quantity x presentation weight) that reports and cost use. All null for other lines
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentation_id", updatable = false)
    private ProductPresentation presentation;

    @Column(name = "presentation_name", length = 50, updatable = false)
    private String presentationName;

    @Column(name = "presentation_weight_kg", precision = 12, scale = 3, updatable = false)
    private BigDecimal presentationWeightKg;

    @Column(name = "weight_kg", precision = 12, scale = 3, updatable = false)
    private BigDecimal weightKg;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
