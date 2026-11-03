package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.DeliveryStatus;
import com.akhana.akhana_admin.model.DeliveryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequest(
    @NotNull(message = "El proveedor es obligatorio.")
    UUID supplierId,

    // Null means today (Bolivia time)
    LocalDate purchaseDate,

    @NotEmpty(message = "El pedido debe tener al menos una línea.")
    List<@Valid PurchaseItemRequest> items,

    @DecimalMin(value = "0.00", message = "El costo de envío no puede ser negativo.")
    BigDecimal shippingCost,

    // Account that paid the shipping; required when shippingCost > 0 (P-24)
    UUID shippingAccountId,

    Boolean consignment,

    @Size(max = 500, message = "El comentario no puede exceder 500 caracteres.")
    String comment,

    // COM-07: null type means in store, null status means pending
    DeliveryType deliveryType,

    DeliveryStatus deliveryStatus,

    LocalDate deliveryDate,

    // Optional first payment, from an account or a person, saved with the purchase (COM-05, DEU-02)
    @Valid PurchasePaymentRequest initialPayment,

    // COM-20: the reading this purchase comes from, if any
    UUID readingId
) {
    public PurchaseRequest(UUID supplierId, LocalDate purchaseDate, List<PurchaseItemRequest> items, BigDecimal shippingCost,
                           UUID shippingAccountId, Boolean consignment, String comment, DeliveryType deliveryType,
                           DeliveryStatus deliveryStatus, LocalDate deliveryDate, PurchasePaymentRequest initialPayment) {
        this(supplierId, purchaseDate, items, shippingCost, shippingAccountId, consignment, comment, deliveryType,
            deliveryStatus, deliveryDate, initialPayment, null);
    }
}
