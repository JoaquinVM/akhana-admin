package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The dashboard (DSH-01 to DSH-04): the period's indicators against the previous period of the same length, sales
 * per day, how they were paid, the top products and suppliers, the expenses per category and what is true right
 * now (cash session, account balances and debts). Sales, cost and profit are those of the report per day (FIN-10):
 * at-cost sales and consumptions go apart (CON-03) and voided records never count.
 */
public record DashboardResponse(LocalDate from,
                                LocalDate to,
                                LocalDate previousFrom,
                                LocalDate previousTo,
                                Indicators current,
                                Indicators previous,
                                List<DayPoint> days,
                                List<DayPoint> previousDays,
                                Payments payments,
                                List<RankRow> topProductsBySales,
                                List<RankRow> topProductsByProfit,
                                List<RankRow> topSuppliersBySales,
                                List<RankRow> topSuppliersByProfit,
                                List<CategoryAmount> expenses,
                                BigDecimal expenseTotal,
                                Now now) {

    /**
     * Normal sales of a period: total, cost, profit, margin % (null without sales), tickets and average ticket
     * (null without tickets); at-cost sales and consumptions at cost apart.
     */
    public record Indicators(BigDecimal sales,
                             BigDecimal cost,
                             BigDecimal profit,
                             BigDecimal margin,
                             long tickets,
                             BigDecimal averageTicket,
                             long atCostSales,
                             BigDecimal atCostTotal,
                             BigDecimal consumptionTotal) {
    }

    /** One store day with normal sales and their profit. */
    public record DayPoint(LocalDate date, BigDecimal sales, BigDecimal profit) {
    }

    /** What the period's active sales were paid with. */
    public record Payments(BigDecimal cash, BigDecimal qr, BigDecimal credit) {
    }

    /** A product or supplier with its normal sales; {@code id} is null for historical lines without supplier. */
    public record RankRow(UUID id, String name, BigDecimal quantity, BigDecimal sales, BigDecimal profit) {
    }

    /** An expense category with its positive sum; {@code categoryId} is null for movements without one. */
    public record CategoryAmount(UUID categoryId, String categoryName, BigDecimal amount) {
    }

    /** Right now, whatever the period: the cash session, the account balances and the debts. */
    public record Now(CashNow cash,
                      List<AccountBalance> accounts,
                      BigDecimal totalBalance,
                      BigDecimal payables,
                      BigDecimal creditorsDebt,
                      BigDecimal receivables) {
    }

    /** The open cash session, or {@code open = false} with the rest empty. */
    public record CashNow(boolean open,
                          Long sessionNumber,
                          String openedBy,
                          Instant openedAt,
                          BigDecimal openingAmount,
                          BigDecimal totalSales,
                          long salesCount,
                          BigDecimal expectedCash) {
    }

    public record AccountBalance(UUID id, String name, String color, BigDecimal balance) {
    }
}
