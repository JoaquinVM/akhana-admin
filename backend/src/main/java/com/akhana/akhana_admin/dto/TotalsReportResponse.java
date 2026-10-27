package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sales and profit totals per store month (FIN-10): normal sales, at-cost sales and consumptions at cost apart
 * (CON-03). The profit is the normal sales' one; consumptions only lower {@code profitAfterConsumptions}.
 */
public record TotalsReportResponse(LocalDate from, LocalDate to, List<Row> months, Row totals) {

    /**
     * One month ("2026-07"), or the sum of the range when {@code month} is null. The sales figures are the ones of
     * sales per day (so they always match it) and the margin is null when there are no normal sales.
     */
    public record Row(String month,
                      long normalSales,
                      BigDecimal normalTotal,
                      BigDecimal normalCost,
                      BigDecimal normalProfit,
                      BigDecimal normalMargin,
                      long atCostSales,
                      BigDecimal atCostTotal,
                      // POS-12, POS-13: same meaning as in sales per day
                      BigDecimal pendingTotal,
                      BigDecimal thirdPartyTotal,
                      BigDecimal total,
                      long consumptions,
                      BigDecimal consumptionTotal,
                      BigDecimal profitAfterConsumptions) {
    }
}
