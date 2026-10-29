package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String color = "#2E5B27";

    @Column(name = "initial_balance", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(name = "current_balance", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "is_cash_account", nullable = false)
    @Builder.Default
    private Boolean isCashAccount = false;

    @Column(name = "is_qr_account", nullable = false)
    @Builder.Default
    private Boolean isQrAccount = false;

    // Audit
    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    @Builder.Default
    private String createdBy = "SYSTEM";

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

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (color == null || color.isBlank()) {
            color = "#2E5B27";
        }
        if (initialBalance == null) {
            initialBalance = BigDecimal.ZERO;
        }
        if (currentBalance == null) {
            currentBalance = initialBalance;
        }
        if (isCashAccount == null) {
            isCashAccount = false;
        }
        if (isQrAccount == null) {
            isQrAccount = false;
        }
    }
}
