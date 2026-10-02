package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SaleResponse(
    UUID id,
    String saleNumber,
    UUID cashSessionId,
    BigDecimal totalAmount,
    PaymentMethod paymentMethod,
    String description,
    String createdBy,
    Instant createdAt
) {}
