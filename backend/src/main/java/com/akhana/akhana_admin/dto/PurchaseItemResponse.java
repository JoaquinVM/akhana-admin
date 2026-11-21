package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PurchaseItem;
import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseItemResponse(
    UUID id,
    UUID productId,
    String productName,
    String productCode,
    SaleUnit saleUnit,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal subtotal,
    BigDecimal previousUnitCost,
    boolean costUpdated,
    // COM-29: copy of the package bought, the base units of the line and the cost of one; the package fields are
    // null for a line in base units, whose base quantity is its quantity
    UUID presentationId,
    String presentationName,
    BigDecimal presentationBaseQuantity,
    BigDecimal baseQuantity,
    BigDecimal baseUnitCost
) {
    public static PurchaseItemResponse fromEntity(PurchaseItem item) {
        return new PurchaseItemResponse(
            item.getId(),
            item.getProduct().getId(),
            item.getProductName(),
            item.getProductCode(),
            item.getProduct().getSaleUnit(),
            item.getQuantity(),
            item.getUnitCost(),
            item.getSubtotal(),
            item.getPreviousUnitCost(),
            Boolean.TRUE.equals(item.getCostUpdated()),
            item.getPresentation() != null ? item.getPresentation().getId() : null,
            item.getPresentationName(),
            item.getPresentationBaseQuantity(),
            item.getBaseQuantity() != null ? item.getBaseQuantity() : item.getQuantity(),
            item.baseUnitCost()
        );
    }
}
