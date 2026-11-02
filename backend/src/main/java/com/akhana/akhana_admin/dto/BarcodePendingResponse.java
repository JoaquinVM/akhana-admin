package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

// CAT-10: a row of «Cargar códigos»; the list is ordered by sales, but no sales figure travels with it
public record BarcodePendingResponse(UUID id, String code, String name, String supplierName, BigDecimal sellPrice) {
}
