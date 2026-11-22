package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(length = 500)
    private String description;

    @Column(name = "buy_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyPrice;

    @Column(name = "sell_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellPrice;

    @Column(name = "fixed_profit", nullable = false, precision = 12, scale = 2)
    private BigDecimal fixedProfit;

    @Column(name = "percentage_profit", nullable = false, precision = 8, scale = 2)
    private BigDecimal percentageProfit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVO;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_unit", nullable = false, length = 20)
    @Builder.Default
    private SaleUnit saleUnit = SaleUnit.UNIT;

    // POS-11: null for normal products; set on create and never changed
    @Enumerated(EnumType.STRING)
    @Column(name = "special_type", length = 20, updatable = false)
    private ProductSpecialType specialType;

    // POS-13: owner of a third-party product
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_creditor_id", updatable = false)
    private Creditor ownerCreditor;

    // Batch-loaded like the presentations, so a page of products reads its tags in one query
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @JoinTable(
        name = "product_tags",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    // CAT-15: packages (the presentations of CAT-11); batch-loaded so the product list stays one query per page
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("baseQuantity ASC")
    @BatchSize(size = 100)
    @Builder.Default
    private List<ProductPresentation> presentations = new ArrayList<>();

    // CAT-12: read in the same query as the product, so the catalog does not load photos one by one
    @Formula("(SELECT pp.id FROM product_photos pp WHERE pp.product_id = id AND pp.active)")
    private UUID activePhotoId;

    // CAT-10: factory barcodes of the product and of its packages (CAT-15), active and removed; batch-loaded like the
    // presentations
    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @BatchSize(size = 100)
    @Builder.Default
    private List<ProductBarcode> barcodes = new ArrayList<>();

    // CAT-05: the product controls its stock; never the special ones (POS-11, POS-13)
    @Column(name = "stock_controlled", nullable = false)
    @Builder.Default
    private boolean stockControlled = true;

    // INV-01: read-only copy of the stock shortcut (product_stocks), read with the product so the catalog is one query
    @Formula("(SELECT s.quantity FROM product_stocks s WHERE s.product_id = id)")
    private BigDecimal stockQuantity;

    @Formula("(SELECT s.stock_value FROM product_stocks s WHERE s.product_id = id)")
    private BigDecimal stockValue;

    @Formula("(SELECT s.average_cost FROM product_stocks s WHERE s.product_id = id)")
    private BigDecimal stockAverageCost;

    @Formula("(SELECT s.started_at FROM product_stocks s WHERE s.product_id = id)")
    private Instant stockStartedAt;

    // CAT-10: «No trae», checked and without a factory barcode
    @Column(name = "no_barcode_marked_by", length = 100)
    private String noBarcodeMarkedBy;

    @Column(name = "no_barcode_marked_at")
    private Instant noBarcodeMarkedAt;

    // Audit fields
    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isSpecial() {
        return specialType != null;
    }

    /** CAT-05, INV-18: it moves stock now: it controls it, is not special and was counted or created in the V2. */
    public boolean controlsStockNow() {
        return stockControlled && !isSpecial() && stockStartedAt != null;
    }
}
