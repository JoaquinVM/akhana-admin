package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PurchaseTextReadingRequest(
    @NotBlank(message = "Pega el texto del pedido.")
    @Size(max = 5000, message = "El texto no puede exceder 5000 caracteres.")
    String text,

    // Optional: the supplier the person already knows it is for
    UUID supplierId
) {}
