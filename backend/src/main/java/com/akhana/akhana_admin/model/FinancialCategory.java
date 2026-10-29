package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "financial_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinancialCategoryType type;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String color = "#2E5B27";

    @Column(name = "is_sales_default", nullable = false)
    @Builder.Default
    private Boolean isSalesDefault = false;

    // Set on the categories the code needs (Pedido, Transporte); those cannot be deleted
    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", length = 30, updatable = false)
    private FinancialCategorySystemKey systemKey;

    // Auditoría
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

    /**
     * Categories the system records by itself (Pedido, Pago a tercero, Ajuste) and the sales one only come from
     * their own flows (Compras, Deudas, cierre de caja, ventas); a manual movement cannot use them. Transporte is
     * also a system category, but it is an ordinary expense too.
     */
    public boolean isManualEntryAllowed() {
        if (Boolean.TRUE.equals(isSalesDefault)) {
            return false;
        }
        return systemKey == null || systemKey == FinancialCategorySystemKey.PURCHASE_SHIPPING;
    }

    /** CAJ-09, CAJ-10: Ajuste movements are generated when opening or closing the cash session. */
    public boolean isCashAdjustment() {
        return systemKey == FinancialCategorySystemKey.CASH_SURPLUS || systemKey == FinancialCategorySystemKey.CASH_SHORTAGE;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (color == null || color.isBlank()) {
            color = "#2E5B27";
        }
        if (isSalesDefault == null) {
            isSalesDefault = false;
        }
    }
}
