package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "product_code", length = 50)
    private String productCode;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Product cost at the moment of the sale (POS-05); never updated afterwards
    @Column(name = "unit_cost", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "discount_per_unit", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountPerUnit = BigDecimal.ZERO;

    @Column(name = "final_unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalUnitPrice;

    // Up to 3 decimals for products sold by weight (CAT-08)
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

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
