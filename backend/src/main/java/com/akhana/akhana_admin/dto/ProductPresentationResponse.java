package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductPresentation;
import com.akhana.akhana_admin.service.impl.ProductPresentationServiceImpl;

import java.math.BigDecimal;
import java.util.UUID;

/** A presentation with the price and cost the POS uses, both computed from the product (CAT-11). */
public record ProductPresentationResponse(
    UUID id,
    String name,
    BigDecimal weightKg,
    BigDecimal fixedPrice,
    BigDecimal price,
    BigDecimal cost,
    boolean active
) {
    public static ProductPresentationResponse fromEntity(ProductPresentation presentation, Product product) {
        return new ProductPresentationResponse(
            presentation.getId(),
            presentation.getName(),
            presentation.getWeightKg(),
            presentation.getFixedPrice(),
            ProductPresentationServiceImpl.priceOf(presentation, product),
            ProductPresentationServiceImpl.costOf(presentation, product),
            presentation.isActive()
        );
    }
}
