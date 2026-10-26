package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse.Month;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse.Row;
import com.akhana.akhana_admin.dto.SalesBySupplierReportResponse;
import com.akhana.akhana_admin.model.SaleType;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    static final int MAX_RANGE_DAYS = 366;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SaleRepository saleRepository;

    @Override
    @Transactional(readOnly = true)
    public SalesByDayReportResponse getSalesByDay(LocalDate from, LocalDate to) {
        Range range = Range.of(from, to);

        Map<LocalDate, Sums> days = new TreeMap<>();
        for (Object[] row : saleRepository.sumByDayAndType(range.fromInstant(), range.toInstant())) {
            Sums day = days.computeIfAbsent(LocalDate.parse((String) row[0]), d -> new Sums());
            long sales = ((Number) row[2]).longValue();
            BigDecimal quantity = (BigDecimal) row[3];
            BigDecimal total = (BigDecimal) row[4];
            if (SaleType.AL_COSTO.name().equals(row[1])) {
                day.atCostSales += sales;
                day.atCostQuantity = day.atCostQuantity.add(quantity);
                day.atCostTotal = day.atCostTotal.add(total);
            } else {
                day.normalSales += sales;
                day.normalQuantity = day.normalQuantity.add(quantity);
                day.normalTotal = day.normalTotal.add(total);
                // Each day's cost is rounded once; months and the range add up the rounded days
                day.normalCost = day.normalCost.add(money((BigDecimal) row[5]));
            }
        }

        Map<YearMonth, List<LocalDate>> byMonth = new TreeMap<>();
        days.keySet().forEach(date -> byMonth.computeIfAbsent(YearMonth.from(date), m -> new ArrayList<>()).add(date));
        List<Month> months = new ArrayList<>();
        Sums rangeSums = new Sums();
        byMonth.forEach((month, dates) -> {
            Sums monthSums = new Sums();
            List<Row> rows = dates.stream().map(date -> {
                Sums day = days.get(date);
                monthSums.add(day);
                return day.toRow(date);
            }).toList();
            rangeSums.add(monthSums);
            months.add(new Month(month.toString(), rows, monthSums.toRow(null)));
        });
        return new SalesByDayReportResponse(range.from(), range.to(), months, rangeSums.toRow(null));
    }

    @Override
    @Transactional(readOnly = true)
    public SalesBySupplierReportResponse getSalesBySupplier(LocalDate from, LocalDate to) {
        Range range = Range.of(from, to);

        // Lines grouped by sale, to spread each sale's general discount over its own lines
        Map<Object, List<Object[]>> sales = new LinkedHashMap<>();
        for (Object[] line : saleRepository.findReportLines(range.fromInstant(), range.toInstant())) {
            sales.computeIfAbsent(line[0], id -> new ArrayList<>()).add(line);
        }

        Map<SupplierKey, Map<String, SupplierSums>> bySupplier = new HashMap<>();
        for (List<Object[]> lines : sales.values()) {
            List<BigDecimal> lineTotals = lineTotalsAfterGeneralDiscount(
                lines.stream().map(line -> (BigDecimal) line[7]).toList(), (BigDecimal) lines.getFirst()[3]);
            for (int i = 0; i < lines.size(); i++) {
                Object[] line = lines.get(i);
                SupplierKey supplier = new SupplierKey((UUID) line[4], (String) line[5]);
                SupplierSums month = bySupplier.computeIfAbsent(supplier, s -> new TreeMap<>())
                    .computeIfAbsent((String) line[1], m -> new SupplierSums());
                BigDecimal quantity = (BigDecimal) line[6];
                if (SaleType.AL_COSTO.name().equals(line[2])) {
                    month.atCostQuantity = month.atCostQuantity.add(quantity);
                    month.atCostTotal = month.atCostTotal.add(lineTotals.get(i));
                } else {
                    month.normalQuantity = month.normalQuantity.add(quantity);
                    month.normalTotal = month.normalTotal.add(lineTotals.get(i));
                    month.normalCost = month.normalCost.add((BigDecimal) line[8]);
                }
            }
        }

        List<SalesBySupplierReportResponse.Supplier> suppliers = new ArrayList<>();
        SupplierSums rangeSums = new SupplierSums();
        bySupplier.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(SupplierKey.ORDER))
            .forEach(entry -> {
                SupplierSums supplierSums = new SupplierSums();
                List<SalesBySupplierReportResponse.Row> rows = entry.getValue().entrySet().stream().map(month -> {
                    // Each supplier month's cost is rounded once; the supplier and the range add up the rounded months
                    month.getValue().normalCost = money(month.getValue().normalCost);
                    supplierSums.add(month.getValue());
                    return month.getValue().toRow(month.getKey());
                }).toList();
                rangeSums.add(supplierSums);
                suppliers.add(new SalesBySupplierReportResponse.Supplier(entry.getKey().id(), entry.getKey().name(),
                    rows, supplierSums.toRow(null)));
            });
        return new SalesBySupplierReportResponse(range.from(), range.to(), suppliers, rangeSums.toRow(null));
    }

    /**
     * Spreads a sale's general discount (line subtotals - sale total) over its lines in proportion to their
     * subtotal. The rounding remainder goes to the line with the largest subtotal, so the lines always add up to
     * the sale total and the suppliers of a day add up to that day's sales.
     */
    static List<BigDecimal> lineTotalsAfterGeneralDiscount(List<BigDecimal> subtotals, BigDecimal saleTotal) {
        BigDecimal sum = subtotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = sum.subtract(saleTotal);
        if (discount.signum() == 0 || sum.signum() == 0) {
            return subtotals.stream().map(ReportServiceImpl::money).toList();
        }
        List<BigDecimal> totals = new ArrayList<>();
        BigDecimal spread = BigDecimal.ZERO;
        int largest = 0;
        for (int i = 0; i < subtotals.size(); i++) {
            BigDecimal share = money(discount.multiply(subtotals.get(i)).divide(sum, 10, RoundingMode.HALF_UP));
            spread = spread.add(share);
            totals.add(money(subtotals.get(i).subtract(share)));
            if (subtotals.get(i).compareTo(subtotals.get(largest)) > 0) {
                largest = i;
            }
        }
        totals.set(largest, totals.get(largest).subtract(discount.subtract(spread)));
        return totals;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    /** A store-day range: "to" includes the whole day, like the sales history (POS-09). */
    private record Range(LocalDate from, LocalDate to) {

        static Range of(LocalDate from, LocalDate to) {
            LocalDate end = to != null ? to : LocalDate.now(PurchaseServiceImpl.STORE_ZONE);
            LocalDate start = from != null ? from : end.withDayOfMonth(1);
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta.");
            }
            if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
                throw new IllegalArgumentException("El rango del reporte no puede superar un año.");
            }
            return new Range(start, end);
        }

        Instant fromInstant() {
            return from.atStartOfDay(PurchaseServiceImpl.STORE_ZONE).toInstant();
        }

        Instant toInstant() {
            return to.plusDays(1).atStartOfDay(PurchaseServiceImpl.STORE_ZONE).toInstant();
        }
    }

    /** A supplier of the report; historical lines without one (FIN-11) go last as "Sin proveedor". */
    private record SupplierKey(UUID id, String name) {

        static final String WITHOUT_SUPPLIER = "Sin proveedor";
        static final Comparator<SupplierKey> ORDER = Comparator
            .comparing((SupplierKey key) -> key.id() == null)
            .thenComparing(SupplierKey::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(key -> String.valueOf(key.id()));

        SupplierKey {
            if (id == null) {
                name = WITHOUT_SUPPLIER;
            }
        }
    }

    /** Running sums of one supplier month, supplier or range. */
    private static final class SupplierSums {
        BigDecimal normalQuantity = BigDecimal.ZERO;
        BigDecimal normalTotal = BigDecimal.ZERO;
        BigDecimal normalCost = BigDecimal.ZERO;
        BigDecimal atCostQuantity = BigDecimal.ZERO;
        BigDecimal atCostTotal = BigDecimal.ZERO;

        void add(SupplierSums other) {
            normalQuantity = normalQuantity.add(other.normalQuantity);
            normalTotal = normalTotal.add(other.normalTotal);
            normalCost = normalCost.add(other.normalCost);
            atCostQuantity = atCostQuantity.add(other.atCostQuantity);
            atCostTotal = atCostTotal.add(other.atCostTotal);
        }

        SalesBySupplierReportResponse.Row toRow(String month) {
            BigDecimal profit = money(normalTotal.subtract(normalCost));
            BigDecimal margin = normalTotal.signum() > 0
                ? profit.multiply(HUNDRED).divide(normalTotal, 2, RoundingMode.HALF_UP)
                : null;
            return new SalesBySupplierReportResponse.Row(month, quantity(normalQuantity), money(normalTotal),
                money(normalCost), profit, margin, quantity(atCostQuantity), money(atCostTotal),
                money(normalTotal.add(atCostTotal)));
        }
    }

    /** Running sums of one day, month or range. */
    private static final class Sums {
        long normalSales;
        BigDecimal normalQuantity = BigDecimal.ZERO;
        BigDecimal normalTotal = BigDecimal.ZERO;
        BigDecimal normalCost = BigDecimal.ZERO;
        long atCostSales;
        BigDecimal atCostQuantity = BigDecimal.ZERO;
        BigDecimal atCostTotal = BigDecimal.ZERO;

        void add(Sums other) {
            normalSales += other.normalSales;
            normalQuantity = normalQuantity.add(other.normalQuantity);
            normalTotal = normalTotal.add(other.normalTotal);
            normalCost = normalCost.add(other.normalCost);
            atCostSales += other.atCostSales;
            atCostQuantity = atCostQuantity.add(other.atCostQuantity);
            atCostTotal = atCostTotal.add(other.atCostTotal);
        }

        Row toRow(LocalDate date) {
            BigDecimal profit = money(normalTotal.subtract(normalCost));
            BigDecimal margin = normalTotal.signum() > 0
                ? profit.multiply(HUNDRED).divide(normalTotal, 2, RoundingMode.HALF_UP)
                : null;
            return new Row(date, normalSales, quantity(normalQuantity), money(normalTotal), money(normalCost), profit,
                margin, atCostSales, quantity(atCostQuantity), money(atCostTotal), money(normalTotal.add(atCostTotal)),
                profit);
        }
    }
}
