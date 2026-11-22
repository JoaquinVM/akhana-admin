package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * INV-03: the valued kardex of a product in a range of store days: the balance that opens it, each row with its
 * quantity, unit cost and value, and the balance in quantity, value and average after it.
 */
public record KardexResponse(
    UUID productId,
    String code,
    String name,
    SaleUnit saleUnit,
    String status,
    LocalDate from,
    LocalDate to,
    BigDecimal openingQuantity,
    BigDecimal openingValue,
    BigDecimal openingAverageCost,
    List<Row> rows,
    BigDecimal quantity,
    BigDecimal value,
    BigDecimal averageCost,
    List<StockItemResponse.PackageItem> packages
) {
    public record Row(
        UUID id,
        Instant createdAt,
        String type,
        String typeLabel,
        String reference,
        String reason,
        String comment,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal value,
        BigDecimal expensedValue,
        BigDecimal balanceQuantity,
        BigDecimal balanceValue,
        BigDecimal balanceAverageCost,
        String createdBy
    ) {
    }
}
