package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
    UUID id,
    UUID productId,
    String productName,
    String productCode,
    BigDecimal unitPrice,
    BigDecimal discountPerUnit,
    BigDecimal finalUnitPrice,
    BigDecimal quantity,
    BigDecimal subtotal,
    // CAT-11: copy of the presentation sold and the kilos of the line; null for other lines
    UUID presentationId,
    String presentationName,
    BigDecimal presentationWeightKg,
    BigDecimal weightKg
) {}
