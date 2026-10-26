package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.SaleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

    String description,

    // Null means NORMAL (CON-02)
    SaleType saleType,

    // Required only when part of an at-cost sale is on credit (DEU-05)
    UUID customerId,

    // Part left as a receivable; null means 0 (DEU-05)
    BigDecimal creditAmount
) {
    public SaleDetailRequest(List<SaleItemRequest> items, BigDecimal globalDiscountAmount, PaymentMethod paymentMethod,
                             BigDecimal amountCash, BigDecimal amountQr, BigDecimal amountReceived, String description) {
        this(items, globalDiscountAmount, paymentMethod, amountCash, amountQr, amountReceived, description, null, null, null);
    }
}
