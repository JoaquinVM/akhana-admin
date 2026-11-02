package com.akhana.akhana_admin.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductBarcodeRequest(
    @NotBlank(message = "El código de barras no es válido.")
    String barcode
) {
}
