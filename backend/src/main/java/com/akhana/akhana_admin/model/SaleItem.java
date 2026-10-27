package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
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

    // Product, name, code and cost are a copy of the moment of the sale; they change only when an unidentified line
    // is assigned (POS-12), through SaleItemRepository.assign, never by writing the entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 150, updatable = false)
    private String productName;

    @Column(name = "product_code", length = 50, updatable = false)
    private String productCode;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Product cost at the moment of the sale (POS-05); never updated afterwards, except once when an
    // unidentified line is assigned (POS-12)
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

    // POS-11: free line of a special product, with the name typed in the POS (kept after an assignment)
    @Enumerated(EnumType.STRING)
    @Column(name = "free_line_type", length = 20, updatable = false)
    private ProductSpecialType freeLineType;

    @Column(name = "free_name", length = 150, updatable = false)
    private String freeName;

    // POS-12: only for unidentified lines
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", length = 20, updatable = false)
    private SaleItemAssignmentStatus assignmentStatus;

    @Column(name = "assigned_by", length = 100, updatable = false)
    private String assignedBy;

    @Column(name = "assigned_at", updatable = false)
    private Instant assignedAt;

    // POS-13: owner of a third-party line, copied from the product at the moment of the sale
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id", updatable = false)
    private Creditor creditor;
}
