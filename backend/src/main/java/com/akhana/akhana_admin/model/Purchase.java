package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FIN-11: import of the historical migration that created it; null for records of the system
    @Column(name = "historical_import_id", updatable = false)
    private UUID historicalImportId;

    @Column(name = "purchase_number", nullable = false, length = 50, updatable = false)
    private String purchaseNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.REGISTRADO;

    // Sum of the lines: what is owed to the supplier (COM-04)
    @Column(name = "items_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal itemsTotal;

    // Paid apart as a Transporte expense (P-24)
    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Sum of active payments, kept under the purchase lock
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean consignment = false;

    @Column(length = 500)
    private String comment;

    // COM-07: while pending the date is the expected one; once delivered, the delivery date
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 20)
    @Builder.Default
    private DeliveryType deliveryType = DeliveryType.EN_TIENDA;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDIENTE;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL)
    @OrderBy("id")
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL)
    @OrderBy("paymentDate")
    @Builder.Default
    private List<PurchasePayment> payments = new ArrayList<>();

    // COM-12: edits with who, when, why and what changed, the most recent first
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL)
    @OrderBy("revisedAt DESC")
    @Builder.Default
    private List<PurchaseRevision> revisions = new ArrayList<>();

    public BigDecimal getBalance() {
        BigDecimal balance = itemsTotal.subtract(paidAmount);
        return balance.signum() > 0 ? balance : BigDecimal.ZERO.setScale(2);
    }

    public PurchasePaymentStatus getPaymentStatus() {
        if (paidAmount.compareTo(itemsTotal) >= 0) {
            return PurchasePaymentStatus.PAGADO;
        }
        if (paidAmount.signum() > 0) {
            return PurchasePaymentStatus.A_CUENTA;
        }
        return Boolean.TRUE.equals(consignment) ? PurchasePaymentStatus.CONSIGNACION : PurchasePaymentStatus.POR_PAGAR;
    }

    /** FIN-11: imported from the spreadsheets; it cannot be voided or edited. */
    public boolean isHistorical() {
        return historicalImportId != null;
    }
}
