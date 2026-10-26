package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.ConsumptionItem;
import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record ConsumptionItemResponse(
    UUID id,
    UUID productId,
    String productName,
    String productCode,
    SaleUnit saleUnit,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal subtotal,
    // CAT-11: copy of the presentation consumed and the kilos of the line; null for other lines
    UUID presentationId,
    String presentationName,
    BigDecimal presentationWeightKg,
    BigDecimal weightKg
) {
    public static ConsumptionItemResponse fromEntity(ConsumptionItem item) {
        return new ConsumptionItemResponse(
            item.getId(),
            item.getProduct().getId(),
            item.getProductName(),
            item.getProductCode(),
            item.getProduct().getSaleUnit(),
            item.getQuantity(),
            item.getUnitCost(),
            item.getSubtotal(),
            item.getPresentation() != null ? item.getPresentation().getId() : null,
            item.getPresentationName(),
            item.getPresentationWeightKg(),
            item.getWeightKg()
        );
    }
}
