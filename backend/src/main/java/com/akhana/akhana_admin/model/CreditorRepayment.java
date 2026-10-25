package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Money the business gives back to a person (DEU-03); its expense points to it (creditor_repayment_id). */
@Entity
@Table(name = "creditor_repayments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditorRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id", nullable = false)
    private Creditor creditor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private FinancialAccount account;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "repayment_date", nullable = false)
    private Instant repaymentDate;

    @Column(length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CreditorRepaymentStatus status = CreditorRepaymentStatus.ACTIVO;

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
