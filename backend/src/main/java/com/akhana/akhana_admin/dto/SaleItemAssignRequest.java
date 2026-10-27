package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Real product of an unidentified line (POS-12). */
public record SaleItemAssignRequest(
    @NotNull(message = "Elija el producto de la línea.")
    UUID productId
) {}
