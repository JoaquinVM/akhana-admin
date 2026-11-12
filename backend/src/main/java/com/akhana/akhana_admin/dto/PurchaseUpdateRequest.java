package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.DeliveryStatus;
import com.akhana.akhana_admin.model.DeliveryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Edit of a registered purchase (COM-11): the whole purchase as it should be. Lines with {@code itemId} are kept
 * (and changed), lines without it are added and the missing ones are removed. Payments are not edited here.
 */
public record PurchaseUpdateRequest(
    @NotNull(message = "El proveedor es obligatorio.")
    UUID supplierId,

    // Null keeps the current date
    LocalDate purchaseDate,

    @NotEmpty(message = "El pedido debe tener al menos una línea.")
    List<@Valid PurchaseItemRequest> items,

    @DecimalMin(value = "0.00", message = "El costo de envío no puede ser negativo.")
    BigDecimal shippingCost,

    // Who paid the shipping, an account or a person (DEU-02); one is required when the shipping changes and is > 0 (COM-12)
    UUID shippingAccountId,

    Boolean consignment,

    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    String comment,

    DeliveryType deliveryType,

    DeliveryStatus deliveryStatus,

    LocalDate deliveryDate,

    @NotBlank(message = "El motivo del cambio es obligatorio.")
    @Size(max = 500, message = "El motivo del cambio no puede exceder 500 caracteres.")
    String reason,

    // COM-22: the goods arrived: it ends delivered, with the real costs and without «costo por confirmar»
    Boolean arrival,

    // COM-24: the photo reading used to fill the arrival, if any
    UUID readingId,

    // DEU-02: the person who paid the shipping, instead of an account
    UUID shippingCreditorId
) {
    public PurchaseUpdateRequest(UUID supplierId, LocalDate purchaseDate, List<PurchaseItemRequest> items, BigDecimal shippingCost,
                                 UUID shippingAccountId, Boolean consignment, String comment, DeliveryType deliveryType,
                                 DeliveryStatus deliveryStatus, LocalDate deliveryDate, String reason, Boolean arrival,
                                 UUID readingId) {
        this(supplierId, purchaseDate, items, shippingCost, shippingAccountId, consignment, comment, deliveryType,
            deliveryStatus, deliveryDate, reason, arrival, readingId, null);
    }

    public PurchaseUpdateRequest(UUID supplierId, LocalDate purchaseDate, List<PurchaseItemRequest> items, BigDecimal shippingCost,
                                 UUID shippingAccountId, Boolean consignment, String comment, DeliveryType deliveryType,
                                 DeliveryStatus deliveryStatus, LocalDate deliveryDate, String reason) {
        this(supplierId, purchaseDate, items, shippingCost, shippingAccountId, consignment, comment, deliveryType,
            deliveryStatus, deliveryDate, reason, null, null, null);
    }
}
