package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.CashSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CashSessionDetailResponse(
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
    List<CashCutDto> cuts,
    List<SaleResponse> sales
) {}
