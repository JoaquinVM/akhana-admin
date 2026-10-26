package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Internal consumption (CON-01): products used in the store, valued at cost. It moves no money. */
@Entity
@Table(name = "consumptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consumption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consumption_number", nullable = false, length = 50, updatable = false)
    private String consumptionNumber;

    @Column(name = "consumption_date", nullable = false)
    private LocalDate consumptionDate;

    // Who consumed it, free text (P-36)
    @Column(name = "consumed_for", length = 150)
    private String consumedFor;

    @Column(length = 500)
    private String comment;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConsumptionStatus status = ConsumptionStatus.REGISTRADO;

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

    @OneToMany(mappedBy = "consumption", cascade = CascadeType.ALL)
    @OrderBy("id")
    @Builder.Default
    private List<ConsumptionItem> items = new ArrayList<>();
}
