package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sales per supplier and store month (FIN-10). Each line goes to the product's current supplier and carries its
 * share of the sale's general discount. Normal and at-cost sales go in separate columns (CON-03): at-cost sales
 * never change the profit or the margin of the normal ones.
 */
public record SalesBySupplierReportResponse(LocalDate from, LocalDate to, List<Supplier> suppliers, Row totals) {

    /** A supplier with its months and their sum; {@code supplierId} is null for historical lines without one. */
    public record Supplier(UUID supplierId, String supplierName, List<Row> months, Row totals) {
    }

    /**
     * One month ("2026-07"), or the sum of several when {@code month} is null. The margin is normal profit / normal
     * total x 100, null when there are no normal sales.
     */
    public record Row(String month,
                      BigDecimal normalQuantity,
                      BigDecimal normalTotal,
                      BigDecimal normalCost,
                      BigDecimal normalProfit,
                      BigDecimal normalMargin,
                      BigDecimal atCostQuantity,
                      BigDecimal atCostTotal,
                      BigDecimal total) {
    }
}
