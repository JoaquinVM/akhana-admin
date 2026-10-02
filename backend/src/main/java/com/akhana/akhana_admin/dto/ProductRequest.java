package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.ProductStatus;
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
    @DecimalMin(value = "0.01", message = "El precio de compra debe ser mayor a 0.")
    BigDecimal buyPrice,

    @NotNull(message = "El precio de venta es obligatorio.")
    @DecimalMin(value = "0.01", message = "El precio de venta debe ser mayor a 0.")
    BigDecimal sellPrice,

    ProductStatus status
) {}
