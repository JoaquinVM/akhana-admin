package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SaleDetailRequest(
    @NotEmpty(message = "La venta debe contener al menos un producto")
    @Valid
    List<SaleItemRequest> items,

    @DecimalMin(value = "0.00", message = "El descuento general no puede ser negativo")
    BigDecimal globalDiscountAmount,

    @NotNull(message = "El método de pago es obligatorio")
    PaymentMethod paymentMethod,

    @DecimalMin(value = "0.00", message = "El monto en efectivo no puede ser negativo")
    BigDecimal amountCash,

    @DecimalMin(value = "0.00", message = "El monto en QR no puede ser negativo")
    BigDecimal amountQr,

    @DecimalMin(value = "0.00", message = "El monto recibido no puede ser negativo")
    BigDecimal amountReceived,

    String description
) {}
