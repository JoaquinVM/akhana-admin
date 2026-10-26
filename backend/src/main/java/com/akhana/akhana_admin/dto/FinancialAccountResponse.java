package com.akhana.akhana_admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAccountResponse {
    private UUID id;
    private String name;
    private String color;
    private BigDecimal initialBalance;
    private BigDecimal currentBalance;
    private Boolean isCashAccount;
    private Boolean isQrAccount;
    // FIN-01: the cutover balance can still be entered (no other movements, and for Caja no cash session yet)
    private Boolean openingBalanceEditable;

    // Auditoría
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String deletedBy;
    private Instant deletedAt;
}
