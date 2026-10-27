package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sales per store day (FIN-10), grouped by month with subtotals. Normal and at-cost sales go in separate columns
 * (CON-03): at-cost sales never change the profit or the margin of the normal ones. Lines waiting for their product
 * (POS-12) are store sales outside the profit until assigned; third-party lines (POS-13) are not store sales.
 */
public record SalesByDayReportResponse(LocalDate from, LocalDate to, List<Month> months, Row totals) {

    /** A month ("2026-07") with its days and their sum. */
    public record Month(String month, List<Row> days, Row totals) {
    }

    /**
     * One day, or the sum of several when {@code date} is null. The margin is normal profit / normal total x 100,
     * null when there are no normal sales.
     */
    public record Row(LocalDate date,
                      long normalSales,
                      BigDecimal normalQuantity,
                      BigDecimal normalTotal,
                      BigDecimal normalCost,
                      BigDecimal normalProfit,
                      BigDecimal normalMargin,
                      long atCostSales,
                      BigDecimal atCostQuantity,
                      BigDecimal atCostTotal,
                      BigDecimal pendingTotal,
                      BigDecimal thirdPartyTotal,
                      BigDecimal total,
                      BigDecimal profit) {
    }
}
