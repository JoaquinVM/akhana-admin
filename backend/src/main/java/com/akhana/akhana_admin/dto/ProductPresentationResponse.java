package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductBarcode;
import com.akhana.akhana_admin.model.ProductPresentation;
import com.akhana.akhana_admin.service.impl.ProductPresentationServiceImpl;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A package with the price and cost the POS uses, both computed from the product (CAT-15). The price is null when the
 * package is not sold.
 */
public record ProductPresentationResponse(
    UUID id,
    String name,
    BigDecimal baseQuantity,
    BigDecimal fixedPrice,
    BigDecimal price,
    BigDecimal cost,
    boolean active,
    boolean sold,
    String barcode
) {
    public static ProductPresentationResponse fromEntity(ProductPresentation presentation, Product product) {
        String barcode = product.getBarcodes() == null ? null : product.getBarcodes().stream()
            .filter(b -> b.isActive() && presentation.getId() != null && presentation.getId().equals(b.getPresentationId()))
            .map(ProductBarcode::getBarcode)
            .findFirst()
            .orElse(null);
        return new ProductPresentationResponse(
            presentation.getId(),
            presentation.getName(),
            presentation.getBaseQuantity(),
            presentation.getFixedPrice(),
            presentation.isSold() ? ProductPresentationServiceImpl.priceOf(presentation, product) : null,
            ProductPresentationServiceImpl.costOf(presentation, product),
            presentation.isActive(),
            presentation.isSold(),
            barcode
        );
    }
}
