package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// COM-24, COM-25: one call to the AI, with what it read and what it cost. Failed calls also count for the monthly cap
@Entity
@Table(name = "purchase_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private PurchaseReadingSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private PurchaseReadingStatus status;

    @Column(nullable = false, length = 50, updatable = false)
    private String model;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String result;

    @Column(name = "input_tokens", updatable = false)
    private Long inputTokens;

    @Column(name = "output_tokens", updatable = false)
    private Long outputTokens;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6, updatable = false)
    private BigDecimal costUsd;

    @Column(name = "purchase_id")
    private UUID purchaseId;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
