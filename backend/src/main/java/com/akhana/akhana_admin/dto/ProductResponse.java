package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String code,
    String name,
    UUID categoryId,
    String categoryName,
    String categoryColor,
    UUID supplierId,
    String supplierName,
    String description,
    List<TagItemResponse> tags,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    BigDecimal fixedProfit,
    BigDecimal percentageProfit,
    ProductStatus status,
    SaleUnit saleUnit,
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt,
    // CAT-11: active and inactive, lightest first; the POS shows only the active ones
    List<ProductPresentationResponse> presentations,
    // Non-blocking notices of a save (loss-making presentation, fixed prices left unchanged); empty otherwise
    List<String> warnings
) {
    public record TagItemResponse(UUID id, String name, String color) {}

    public ProductResponse(UUID id, String code, String name, UUID categoryId, String categoryName,
                           String categoryColor, UUID supplierId, String supplierName, String description,
                           List<TagItemResponse> tags, BigDecimal buyPrice, BigDecimal sellPrice,
                           BigDecimal fixedProfit, BigDecimal percentageProfit, ProductStatus status,
                           SaleUnit saleUnit, String createdBy, Instant createdAt, String updatedBy,
                           Instant updatedAt, String deletedBy, Instant deletedAt) {
        this(id, code, name, categoryId, categoryName, categoryColor, supplierId, supplierName, description, tags,
            buyPrice, sellPrice, fixedProfit, percentageProfit, status, saleUnit, createdBy, createdAt, updatedBy,
            updatedAt, deletedBy, deletedAt, List.of(), List.of());
    }

    public static ProductResponse fromEntity(Product product, List<String> warnings) {
        ProductResponse response = fromEntity(product);
        return new ProductResponse(response.id, response.code, response.name, response.categoryId,
            response.categoryName, response.categoryColor, response.supplierId, response.supplierName,
            response.description, response.tags, response.buyPrice, response.sellPrice, response.fixedProfit,
            response.percentageProfit, response.status, response.saleUnit, response.createdBy, response.createdAt,
            response.updatedBy, response.updatedAt, response.deletedBy, response.deletedAt, response.presentations,
            List.copyOf(warnings));
    }

    public static ProductResponse fromEntity(Product product) {
        if (product == null) {
            return null;
        }

        List<TagItemResponse> tagItems = product.getTags() != null
            ? product.getTags().stream()
                .map(t -> new TagItemResponse(t.getId(), t.getName(), t.getColor()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList()
            : Collections.emptyList();

        List<ProductPresentationResponse> presentations = product.getPresentations() != null
            ? product.getPresentations().stream()
                .map(p -> ProductPresentationResponse.fromEntity(p, product))
                .toList()
            : Collections.emptyList();

        return new ProductResponse(
            product.getId(),
            product.getCode(),
            product.getName(),
            product.getCategory() != null ? product.getCategory().getId() : null,
            product.getCategory() != null ? product.getCategory().getName() : null,
            product.getCategory() != null ? product.getCategory().getColor() : null,
            product.getSupplier() != null ? product.getSupplier().getId() : null,
            product.getSupplier() != null ? product.getSupplier().getName() : null,
            product.getDescription(),
            tagItems,
            product.getBuyPrice(),
            product.getSellPrice(),
            product.getFixedProfit(),
            product.getPercentageProfit(),
            product.getStatus(),
            product.getSaleUnit(),
            product.getCreatedBy(),
            product.getCreatedAt(),
            product.getUpdatedBy(),
            product.getUpdatedAt(),
            product.getDeletedBy(),
            product.getDeletedAt(),
            presentations,
            List.of()
        );
    }
}
