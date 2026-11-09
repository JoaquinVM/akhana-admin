package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemRequest(
    @NotNull(message = "El ID del producto es obligatorio.")
    UUID productId,

    @NotNull(message = "La cantidad es obligatoria.")
    @DecimalMin(value = "0", inclusive = false, message = "La cantidad debe ser mayor a 0.")
    @Digits(integer = 9, fraction = 3, message = "La cantidad admite hasta 3 decimales.")
    BigDecimal quantity,

    @DecimalMin(value = "0.00", message = "El descuento unitario no puede ser negativo.")
    BigDecimal discountPerUnit,

    // CAT-11: sold by presentation; quantity is then the whole number of packs
    UUID presentationId,

    // POS-11: name and price typed in the POS, required for special products and rejected for the others
    String freeName,

    BigDecimal freePrice,

    // POS-15: at least one read of this line was a scan; only for measuring
    Boolean scanned,

    // POS-10: amount typed on a line of «Otro peso», charged instead of weight x price per kilo
    BigDecimal lineTotal
) {
    public SaleItemRequest(UUID productId, BigDecimal quantity, BigDecimal discountPerUnit) {
        this(productId, quantity, discountPerUnit, null);
    }

    public SaleItemRequest(UUID productId, BigDecimal quantity, BigDecimal discountPerUnit, UUID presentationId) {
        this(productId, quantity, discountPerUnit, presentationId, null, null);
    }

    public SaleItemRequest(UUID productId, BigDecimal quantity, BigDecimal discountPerUnit, UUID presentationId,
                           String freeName, BigDecimal freePrice) {
        this(productId, quantity, discountPerUnit, presentationId, freeName, freePrice, null);
    }

    public SaleItemRequest(UUID productId, BigDecimal quantity, BigDecimal discountPerUnit, UUID presentationId,
                           String freeName, BigDecimal freePrice, Boolean scanned) {
        this(productId, quantity, discountPerUnit, presentationId, freeName, freePrice, scanned, null);
    }
}
