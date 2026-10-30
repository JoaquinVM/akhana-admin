package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One purchase line. When {@code updateProductCost} is true and the line cost differs from the product cost,
 * the product takes the line cost and {@code newSellPrice} (null keeps the current price) (COM-03). When editing a
 * purchase, {@code itemId} is the line it keeps; null adds a new line (COM-11).
 */
public record PurchaseItemRequest(
    @NotNull(message = "El producto es obligatorio.")
    UUID productId,

    @NotNull(message = "La cantidad es obligatoria.")
    BigDecimal quantity,

    @NotNull(message = "El costo unitario es obligatorio.")
    @DecimalMin(value = "0.00", message = "El costo unitario no puede ser negativo.")
    BigDecimal unitCost,

    Boolean updateProductCost,

    BigDecimal newSellPrice,

    UUID itemId
) {
    public PurchaseItemRequest(UUID productId, BigDecimal quantity, BigDecimal unitCost, Boolean updateProductCost,
                               BigDecimal newSellPrice) {
        this(productId, quantity, unitCost, updateProductCost, newSellPrice, null);
    }
}
