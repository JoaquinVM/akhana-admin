package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * INV-01, INV-14: a row of Existencias. status: CONTROLLED, TO_COUNT (INV-18) or NOT_CONTROLLED (CAT-05); quantity,
 * average and value only when it controls its stock; incoming is what pending purchases bring («por llegar»).
 */
public record StockItemResponse(
    UUID productId,
    String code,
    String name,
    UUID categoryId,
    String categoryName,
    String supplierName,
    SaleUnit saleUnit,
    String status,
    boolean negative,
    BigDecimal quantity,
    BigDecimal averageCost,
    BigDecimal value,
    BigDecimal incoming,
    List<PackageItem> packages
) {
    /** CAT-15: the active packages, to read the stock as «40 = 3 cajas + 4». */
    public record PackageItem(String name, BigDecimal baseQuantity) {
    }
}
