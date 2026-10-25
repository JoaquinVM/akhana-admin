package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchasePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    // Exactly one source: an account or a person who paid for the business (DEU-02)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private FinancialAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id")
    private Creditor creditor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private Instant paymentDate;

    // The Pedido expense this payment generated (COM-06); null when a person paid (DEU-02)
    @Column(name = "financial_transaction_id")
    private UUID financialTransactionId;

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
