package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// CAJ-14: a reopening with what the undone closing had
public record CashSessionReopeningResponse(
    UUID id,
    String reason,
    String reopenedBy,
    Instant reopenedAt,
    BigDecimal closingAmount,
    String closingComment,
    String closedBy,
    Instant closedAt,
    BigDecimal expectedCash,
    BigDecimal difference,
    List<CashCutDto> cuts,
    BigDecimal closingReserveBills,
    BigDecimal closingReserveCoins
) {}
