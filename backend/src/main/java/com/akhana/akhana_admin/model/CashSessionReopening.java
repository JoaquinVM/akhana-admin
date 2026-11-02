package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** CAJ-14: who reopened a session, when and why, with what the undone closing had. Never changes. */
@Entity
@Table(name = "cash_session_reopenings")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashSessionReopening {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cash_session_id", nullable = false, updatable = false)
    private UUID cashSessionId;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "reopened_by", nullable = false, length = 100, updatable = false)
    private String reopenedBy;

    @Column(name = "reopened_at", nullable = false, updatable = false)
    private Instant reopenedAt;

    @Column(name = "closing_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal closingAmount;

    @Column(name = "closing_comment", length = 500, updatable = false)
    private String closingComment;

    @Column(name = "closed_by", length = 100, updatable = false)
    private String closedBy;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private Instant closedAt;

    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal expectedCash;

    @Column(precision = 12, scale = 2, updatable = false)
    private BigDecimal difference;

    @Column(name = "voided_adjustment_id", updatable = false)
    private UUID voidedAdjustmentId;
}
