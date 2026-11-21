package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One purchase line. When {@code updateProductCost} is true and the line cost differs from the product cost,
 * the product takes the line cost and {@code newSellPrice} (null keeps the current price) (COM-03). When editing a
 * purchase, {@code itemId} is the line it keeps; null adds a new line (COM-11). With a package, the cost compared
 * and taken by the product is the cost of one base unit (COM-29).
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

    UUID itemId,

    // COM-29: bought by package; quantity is then whole packs and unitCost the cost of one package
    UUID presentationId
) {
    public PurchaseItemRequest(UUID productId, BigDecimal quantity, BigDecimal unitCost, Boolean updateProductCost,
                               BigDecimal newSellPrice) {
        this(productId, quantity, unitCost, updateProductCost, newSellPrice, null, null);
    }

    public PurchaseItemRequest(UUID productId, BigDecimal quantity, BigDecimal unitCost, Boolean updateProductCost,
                               BigDecimal newSellPrice, UUID itemId) {
        this(productId, quantity, unitCost, updateProductCost, newSellPrice, itemId, null);
    }
}
