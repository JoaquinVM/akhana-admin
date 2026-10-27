package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cash_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FIN-11: import of the historical migration that created it; null for records of the system
    @Column(name = "historical_import_id", updatable = false)
    private UUID historicalImportId;

    // FIN-11: amounts of Cierre de Caja that the system otherwise computes from movements; null for other sessions
    @Column(name = "historical_other_income", precision = 12, scale = 2)
    private BigDecimal historicalOtherIncome;

    @Column(name = "historical_expenses", precision = 12, scale = 2)
    private BigDecimal historicalExpenses;

    @Column(name = "historical_other_outflow", precision = 12, scale = 2)
    private BigDecimal historicalOtherOutflow;

    @Column(name = "historical_internal_outflow", precision = 12, scale = 2)
    private BigDecimal historicalInternalOutflow;

    @Column(name = "session_number", insertable = false, updatable = false)
    private Long sessionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CashSessionStatus status = CashSessionStatus.ABIERTA;

    @Column(name = "opening_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "opening_comment", length = 500)
    private String openingComment;

    @Column(name = "opened_by", nullable = false, length = 100)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    @Builder.Default
    private Instant openedAt = Instant.now();

    @Column(name = "closing_amount", precision = 12, scale = 2)
    private BigDecimal closingAmount;

    @Column(name = "closing_comment", length = 500)
    private String closingComment;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "total_sales_cash", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSalesCash = BigDecimal.ZERO;

    @Column(name = "total_sales_qr", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSalesQr = BigDecimal.ZERO;

    @Column(name = "total_sales", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal expectedCash = BigDecimal.ZERO;

    @Column(name = "difference", precision = 12, scale = 2)
    private BigDecimal difference;

    @OneToMany(mappedBy = "cashSession", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CashDenominationCut> cuts = new ArrayList<>();

    @OneToMany(mappedBy = "cashSession", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Sale> sales = new ArrayList<>();

    /** FIN-11: imported from the spreadsheets; it cannot be voided or edited. */
    public boolean isHistorical() {
        return historicalImportId != null;
    }
}
