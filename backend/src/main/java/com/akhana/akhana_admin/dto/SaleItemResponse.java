package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.ProductSpecialType;
import com.akhana.akhana_admin.model.SaleItem;
import com.akhana.akhana_admin.model.SaleItemAssignmentStatus;
import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.time.Instant;
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
    // How the product is sold, so a weight line without presentation shows kilos and the price per kilo
    SaleUnit saleUnit,
    // CAT-11: copy of the presentation sold and the kilos of the line; null for other lines
    UUID presentationId,
    String presentationName,
    BigDecimal presentationWeightKg,
    BigDecimal weightKg,
    // POS-11 to POS-13: free line with its typed name, assignment and owner; null for other lines
    ProductSpecialType freeLineType,
    String freeName,
    SaleItemAssignmentStatus assignmentStatus,
    String assignedBy,
    Instant assignedAt,
    UUID creditorId,
    String creditorName
) {
    public static SaleItemResponse fromEntity(SaleItem item) {
        return new SaleItemResponse(
            item.getId(),
            item.getProduct() != null ? item.getProduct().getId() : null,
            item.getProductName(),
            item.getProductCode(),
            item.getUnitPrice(),
            item.getDiscountPerUnit(),
            item.getFinalUnitPrice(),
            item.getQuantity(),
            item.getSubtotal(),
            item.getProduct() != null ? item.getProduct().getSaleUnit() : null,
            item.getPresentation() != null ? item.getPresentation().getId() : null,
            item.getPresentationName(),
            item.getPresentationWeightKg(),
            item.getWeightKg(),
            item.getFreeLineType(),
            item.getFreeName(),
            item.getAssignmentStatus(),
            item.getAssignedBy(),
            item.getAssignedAt(),
            item.getCreditor() != null ? item.getCreditor().getId() : null,
            item.getCreditor() != null ? item.getCreditor().getName() : null
        );
    }
}
