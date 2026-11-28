package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** INV-05: what was counted of a product: whole packages of each kind and the loose base units («3 cajas + 4»). */
public record StockCountLineRequest(List<PackageCount> packages, BigDecimal loose) {

    public record PackageCount(UUID presentationId, BigDecimal quantity) {
    }
}
