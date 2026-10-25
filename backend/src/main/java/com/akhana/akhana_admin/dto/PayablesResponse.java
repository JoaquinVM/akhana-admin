package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Purchases with a balance grouped by supplier (COM-08). */
public record PayablesResponse(
    BigDecimal totalBalance,
    List<SupplierPayables> suppliers
) {
    public record SupplierPayables(
        UUID supplierId,
        String supplierName,
        BigDecimal balance,
        List<PurchaseResponse> purchases
    ) {}
}
