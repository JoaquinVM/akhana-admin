package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.CashSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CashSessionSummaryResponse(
    UUID id,
    Long sessionNumber,
    CashSessionStatus status,
    BigDecimal openingAmount,
    String openingComment,
    String openedBy,
    Instant openedAt,
    BigDecimal closingAmount,
    String closingComment,
    String closedBy,
    Instant closedAt,
    BigDecimal totalSalesCash,
    BigDecimal totalSalesQr,
    BigDecimal totalSales,
    BigDecimal expectedCash,
    BigDecimal difference,
    long salesCount,
    // FIN-11: closed session of Cierre de Caja
    boolean historical,
    // CAJ-11: the day its sales and movements count for; openedAt is the real time it was opened
    LocalDate operationDate
) {}
