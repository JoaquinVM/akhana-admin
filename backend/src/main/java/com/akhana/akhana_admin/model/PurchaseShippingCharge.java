package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shipping of a purchase paid by a person (COM-02, DEU-02): no money movement, it raises the debt with them until it
 * is repaid (DEU-03). A change of the shipping voids it and records another (COM-12).
 */
@Entity
@Table(name = "purchase_shipping_charges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseShippingCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id", nullable = false, updatable = false)
    private Creditor creditor;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "charge_date", nullable = false, updatable = false)
    private Instant chargeDate;

    // CAJ-11: no movement, so it counts for the day it is recorded
    @Column(name = "operation_date", nullable = false, updatable = false)
    private LocalDate operationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchasePaymentRecordStatus status = PurchasePaymentRecordStatus.ACTIVO;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;
}
