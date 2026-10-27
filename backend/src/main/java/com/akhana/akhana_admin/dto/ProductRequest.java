package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.ProductSpecialType;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.SaleUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductRequest(
    @NotBlank(message = "El código es obligatorio.")
    @Size(max = 50, message = "El código no debe superar los 50 caracteres.")
    String code,

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 150, message = "El nombre no debe superar los 150 caracteres.")
    String name,

    @NotNull(message = "La categoría es obligatoria.")
    UUID categoryId,

    @NotNull(message = "El proveedor es obligatorio.")
    UUID supplierId,

    @Size(max = 500, message = "La descripción no debe superar los 500 caracteres.")
    String description,

    List<UUID> tagIds,

    @NotNull(message = "El precio de compra es obligatorio.")
    // CAT-01: 0 is allowed (bonuses without a known cost), with a warning in the response
    @DecimalMin(value = "0.00", message = "El precio de compra no puede ser negativo.")
    BigDecimal buyPrice,

    @NotNull(message = "El precio de venta es obligatorio.")
    // Must be more than 0 except on special products, which keep 0 (POS-11): checked by the service
    @DecimalMin(value = "0.00", message = "El precio de venta debe ser mayor a 0.")
    BigDecimal sellPrice,

    ProductStatus status,

    // Optional: null keeps UNIT on create and the current unit on update
    SaleUnit saleUnit,

    // CAT-11: the whole list for a product sold by weight; null keeps the current presentations
    List<ProductPresentationRequest> presentations,

    // POS-11: null for a normal product; set on create only, the owner is required for THIRD_PARTY (POS-13)
    ProductSpecialType specialType,

    UUID ownerCreditorId
) {
    public ProductRequest(String code, String name, UUID categoryId, UUID supplierId, String description,
                          List<UUID> tagIds, BigDecimal buyPrice, BigDecimal sellPrice, ProductStatus status,
                          SaleUnit saleUnit) {
        this(code, name, categoryId, supplierId, description, tagIds, buyPrice, sellPrice, status, saleUnit, null);
    }

    public ProductRequest(String code, String name, UUID categoryId, UUID supplierId, String description,
                          List<UUID> tagIds, BigDecimal buyPrice, BigDecimal sellPrice, ProductStatus status,
                          SaleUnit saleUnit, List<ProductPresentationRequest> presentations) {
        this(code, name, categoryId, supplierId, description, tagIds, buyPrice, sellPrice, status, saleUnit,
            presentations, null, null);
    }
}
