package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.DeliveryStatus;
import com.akhana.akhana_admin.model.DeliveryType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Delivery of a purchase (COM-07). A null date on a delivered purchase means today (Bolivia time). */
public record PurchaseDeliveryRequest(
    @NotNull(message = "El tipo de entrega es obligatorio.")
    DeliveryType deliveryType,

    @NotNull(message = "El estado de entrega es obligatorio.")
    DeliveryStatus deliveryStatus,

    LocalDate deliveryDate
) {}
