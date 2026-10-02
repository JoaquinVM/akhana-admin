package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cash_denomination_cuts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashDenominationCut {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_session_id", nullable = false)
    private CashSession cashSession;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal denomination;

    @Column(name = "cash_quantity", nullable = false)
    @Builder.Default
    private Integer cashQuantity = 0;

    @Column(name = "reserve_quantity", nullable = false)
    @Builder.Default
    private Integer reserveQuantity = 0;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;
}
