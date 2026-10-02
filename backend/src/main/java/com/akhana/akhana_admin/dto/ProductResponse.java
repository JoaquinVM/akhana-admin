package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductStatus;

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
    String createdBy,
    Instant createdAt,
    String updatedBy,
    Instant updatedAt,
    String deletedBy,
    Instant deletedAt
) {
    public record TagItemResponse(UUID id, String name, String color) {}

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
            product.getCreatedBy(),
            product.getCreatedAt(),
            product.getUpdatedBy(),
            product.getUpdatedAt(),
            product.getDeletedBy(),
            product.getDeletedAt()
        );
    }
}
