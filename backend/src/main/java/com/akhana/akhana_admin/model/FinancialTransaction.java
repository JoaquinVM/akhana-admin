package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinancialTransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private FinancialAccount sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private FinancialAccount destinationAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private FinancialCategory category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FinancialTransactionStatus status = FinancialTransactionStatus.ACTIVO;

    @Column(name = "sale_id")
    private UUID saleId;

    @Column(name = "cash_session_id")
    private UUID cashSessionId;

    // Payment or shipping of a purchase (COM-06, P-24): managed only from Compras
    @Column(name = "purchase_id", updatable = false)
    private UUID purchaseId;

    // Auditoría de registro (independiente de transactionDate)
    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Auditoría de modificación
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Auditoría de anulación
    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (transactionDate == null) {
            transactionDate = Instant.now();
        }
        if (status == null) {
            status = FinancialTransactionStatus.ACTIVO;
        }
    }
}
