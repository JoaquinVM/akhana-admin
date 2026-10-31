package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.CashSessionSummaryResponse;
import com.akhana.akhana_admin.dto.CreditorResponse;
import com.akhana.akhana_admin.dto.CustomerResponse;
import com.akhana.akhana_admin.dto.DashboardResponse;
import com.akhana.akhana_admin.dto.DashboardResponse.AccountBalance;
import com.akhana.akhana_admin.dto.DashboardResponse.CashNow;
import com.akhana.akhana_admin.dto.DashboardResponse.CategoryAmount;
import com.akhana.akhana_admin.dto.DashboardResponse.DayPoint;
import com.akhana.akhana_admin.dto.DashboardResponse.Indicators;
import com.akhana.akhana_admin.dto.DashboardResponse.Now;
import com.akhana.akhana_admin.dto.DashboardResponse.Payments;
import com.akhana.akhana_admin.dto.DashboardResponse.RankRow;
import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse;
import com.akhana.akhana_admin.repository.ConsumptionRepository;
import com.akhana.akhana_admin.repository.DashboardRepository;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.CreditorService;
import com.akhana.akhana_admin.service.CustomerService;
import com.akhana.akhana_admin.service.DashboardService;
import com.akhana.akhana_admin.service.FinancialSummaryService;
import com.akhana.akhana_admin.service.PurchaseService;
import com.akhana.akhana_admin.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The dashboard (DSH-01 to DSH-04). Sales, cost, profit, suppliers and expenses come from the reports (FIN-10), so
 * both screens always show the same numbers; only the payments and the products are summed here.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    static final int TOP_SIZE = 10;

    private final ReportService reportService;
    private final DashboardRepository dashboardRepository;
    private final ConsumptionRepository consumptionRepository;
    private final CashSessionService cashSessionService;
    private final FinancialSummaryService financialSummaryService;
    private final PurchaseService purchaseService;
    private final CreditorService creditorService;
    private final CustomerService customerService;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(LocalDate from, LocalDate to) {
        // The report validates the range and fills in the defaults (this month up to today)
        SalesByDayReportResponse sales = reportService.getSalesByDay(from, to);
        LocalDate start = sales.from();
        LocalDate end = sales.to();
        LocalDate[] previous = previousRange(start, end);
        SalesByDayReportResponse previousSales = reportService.getSalesByDay(previous[0], previous[1]);

        TransactionsByCategoryReportResponse.Month movements = reportService.getTransactionsByCategory(start, end).totals();
        List<CategoryAmount> expenses = movements.expenses().stream()
            .map(row -> new CategoryAmount(row.categoryId(), row.categoryName(), row.amount().negate()))
            .filter(row -> row.amount().signum() != 0)
            .sorted(Comparator.comparing(CategoryAmount::amount).reversed())
            .toList();

        List<RankRow> products = productRows(dashboardRepository.findProductLines(start, end));
        List<RankRow> suppliers = reportService.getSalesBySupplier(start, end).suppliers().stream()
            .map(supplier -> new RankRow(supplier.supplierId(), supplier.supplierName(),
                supplier.totals().normalQuantity(), supplier.totals().normalTotal(), supplier.totals().normalProfit()))
            .filter(row -> row.sales().signum() > 0)
            .toList();

        return new DashboardResponse(start, end, previous[0], previous[1],
            indicators(sales.totals(), consumptionTotal(start, end)),
            indicators(previousSales.totals(), consumptionTotal(previous[0], previous[1])),
            dayPoints(sales, start, end),
            dayPoints(previousSales, previous[0], previous[1]),
            payments(dashboardRepository.sumPayments(start, end)),
            top(products, RankRow::sales), top(products, RankRow::profit),
            top(suppliers, RankRow::sales), top(suppliers, RankRow::profit),
            expenses, money(movements.expenseTotal().negate()),
            now());
    }

    /**
     * The period to compare with: from the 1st of a month up to a later day of it compares with the same days of
     * the month before (Oct 1-30 with Sep 1-30, Mar 1-31 with Feb 1-28), and from a Monday up to a later day of that
     * week with the same days of the week before; any other range with the same number of
     * days just before it (a day with the day before).
     */
    public static LocalDate[] previousRange(LocalDate from, LocalDate to) {
        if (from.getDayOfMonth() == 1 && to.isAfter(from) && YearMonth.from(from).equals(YearMonth.from(to))) {
            YearMonth month = YearMonth.from(from).minusMonths(1);
            return new LocalDate[] {month.atDay(1), month.atDay(Math.min(to.getDayOfMonth(), month.lengthOfMonth()))};
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (from.getDayOfWeek() == DayOfWeek.MONDAY && days > 1 && days <= 7) {
            return new LocalDate[] {from.minusWeeks(1), to.minusWeeks(1)};
        }
        LocalDate previousTo = from.minusDays(1);
        return new LocalDate[] {previousTo.minusDays(days - 1), previousTo};
    }

    private static Indicators indicators(SalesByDayReportResponse.Row totals, BigDecimal consumptionTotal) {
        BigDecimal average = totals.normalSales() > 0
            ? totals.normalTotal().divide(BigDecimal.valueOf(totals.normalSales()), 2, RoundingMode.HALF_UP)
            : null;
        return new Indicators(totals.normalTotal(), totals.normalCost(), totals.normalProfit(), totals.normalMargin(),
            totals.normalSales(), average, totals.atCostSales(), totals.atCostTotal(), consumptionTotal);
    }

    private BigDecimal consumptionTotal(LocalDate from, LocalDate to) {
        return money(consumptionRepository.sumByMonth(from, to).stream()
            .map(row -> (BigDecimal) row[2])
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Every day of the range, with zero on the days without sales, so the chart has no gaps. */
    private static List<DayPoint> dayPoints(SalesByDayReportResponse report, LocalDate from, LocalDate to) {
        Map<LocalDate, SalesByDayReportResponse.Row> days = new HashMap<>();
        report.months().forEach(month -> month.days().forEach(day -> days.put(day.date(), day)));
        List<DayPoint> points = new ArrayList<>();
        BigDecimal zero = money(BigDecimal.ZERO);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            SalesByDayReportResponse.Row day = days.get(date);
            points.add(day != null
                ? new DayPoint(date, day.normalTotal(), day.normalProfit())
                : new DayPoint(date, zero, zero));
        }
        return points;
    }

    private static Payments payments(List<Object[]> rows) {
        Object[] row = rows.isEmpty()
            ? new Object[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L}
            : rows.getFirst();
        return new Payments(money((BigDecimal) row[0]), money((BigDecimal) row[1]), money((BigDecimal) row[2]),
            money((BigDecimal) row[3]), ((Number) row[4]).longValue());
    }

    /**
     * Normal sales per product. Each sale's general discount is spread over its lines like in the report per
     * supplier (P-44: free lines keep their exact amount), so the products of a period add up to its sales.
     */
    private static List<RankRow> productRows(List<Object[]> lines) {
        Map<Object, List<Object[]>> sales = new LinkedHashMap<>();
        for (Object[] line : lines) {
            sales.computeIfAbsent(line[0], id -> new ArrayList<>()).add(line);
        }
        Map<UUID, ProductSums> products = new HashMap<>();
        for (List<Object[]> saleLines : sales.values()) {
            BigDecimal freeTotal = saleLines.stream().filter(DashboardServiceImpl::isFreeLine)
                .map(line -> (BigDecimal) line[5]).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<BigDecimal> discounted = ReportServiceImpl.lineTotalsAfterGeneralDiscount(
                saleLines.stream().filter(line -> !isFreeLine(line)).map(line -> (BigDecimal) line[5]).toList(),
                ((BigDecimal) saleLines.getFirst()[1]).subtract(freeTotal));
            int next = 0;
            for (Object[] line : saleLines) {
                BigDecimal lineTotal = isFreeLine(line) ? (BigDecimal) line[5] : discounted.get(next++);
                // Pending, unidentified and third-party lines are not sales of a product (POS-12, POS-13)
                if (!Boolean.TRUE.equals(line[7])) {
                    continue;
                }
                ProductSums product = products.computeIfAbsent((UUID) line[2], id -> new ProductSums((String) line[3]));
                product.quantity = product.quantity.add((BigDecimal) line[4]);
                product.total = product.total.add(lineTotal);
                product.cost = product.cost.add((BigDecimal) line[6]);
            }
        }
        return products.entrySet().stream().map(entry -> {
            ProductSums sums = entry.getValue();
            BigDecimal total = money(sums.total);
            return new RankRow(entry.getKey(), sums.name, sums.quantity.setScale(3, RoundingMode.HALF_UP), total,
                total.subtract(money(sums.cost)));
        }).toList();
    }

    private static List<RankRow> top(List<RankRow> rows, Function<RankRow, BigDecimal> by) {
        return rows.stream()
            .sorted(Comparator.comparing(by).reversed().thenComparing(RankRow::name, String.CASE_INSENSITIVE_ORDER))
            .limit(TOP_SIZE)
            .toList();
    }

    private Now now() {
        CashNow cash = cashSessionService.findCurrentSession()
            .map(DashboardServiceImpl::cashNow)
            .orElse(new CashNow(false, null, null, null, null, null, 0, null));
        FinancialSummaryResponse summary = financialSummaryService.getSummary();
        List<AccountBalance> accounts = summary.getAccounts().stream()
            .map(account -> new AccountBalance(account.getId(), account.getName(), account.getColor(),
                money(account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO)))
            .toList();
        BigDecimal creditorsDebt = creditorService.getCreditors(null, null).stream()
            .map(CreditorResponse::debt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivables = customerService.getCustomers(null, null).stream()
            .map(CustomerResponse::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Now(cash, accounts, money(summary.getTotalBalance()),
            money(purchaseService.getPayables(null).totalBalance()), money(creditorsDebt), money(receivables));
    }

    private static CashNow cashNow(CashSessionSummaryResponse session) {
        return new CashNow(true, session.sessionNumber(), session.openedBy(), session.openedAt(),
            session.openingAmount(), session.totalSales(), session.salesCount(), session.expectedCash());
    }

    private static boolean isFreeLine(Object[] line) {
        return Boolean.TRUE.equals(line[8]);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Running sums of one product. */
    private static final class ProductSums {
        final String name;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        ProductSums(String name) {
            this.name = name;
        }
    }
}
