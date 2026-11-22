package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * INV-05: before confirming, what each line will do: the difference with what was expected when it was written, in
 * base units and in Bs at the cost it moves at. A first count (INV-18) is the initial load at the last cost.
 */
public record StockCountReviewResponse(
    UUID id,
    long number,
    String categoryName,
    List<Line> lines,
    int notCounted,
    BigDecimal differenceValue
) {
    public record Line(
        UUID productId,
        String code,
        String name,
        SaleUnit saleUnit,
        boolean initialLoad,
        BigDecimal countedQuantity,
        String breakdown,
        BigDecimal expectedQuantity,
        BigDecimal difference,
        BigDecimal unitCost,
        BigDecimal differenceValue
    ) {
    }
}
