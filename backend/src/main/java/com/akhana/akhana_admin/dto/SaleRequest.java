package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaleRequest(
    @NotNull(message = "El monto total de la venta es obligatorio.")
    @DecimalMin(value = "0.01", message = "El monto total debe ser mayor a 0.")
    BigDecimal totalAmount,

    @NotNull(message = "El método de pago es obligatorio.")
    PaymentMethod paymentMethod,

    @Size(max = 255, message = "La descripción no debe superar los 255 caracteres.")
    String description
) {}
