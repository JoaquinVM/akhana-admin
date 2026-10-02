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
    Integer quantity,
    BigDecimal subtotal
) {}
