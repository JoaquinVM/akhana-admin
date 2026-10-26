package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse.Month;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse.Row;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    static final int MAX_RANGE_DAYS = 366;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SaleRepository saleRepository;

    @Override
    @Transactional(readOnly = true)
    public SalesByDayReportResponse getSalesByDay(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(PurchaseServiceImpl.STORE_ZONE);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        validateRange(start, end);
        // Store days (America/La_Paz); "to" includes the whole day, like the sales history (POS-09)
        Instant fromInstant = start.atStartOfDay(PurchaseServiceImpl.STORE_ZONE).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(PurchaseServiceImpl.STORE_ZONE).toInstant();

        Map<LocalDate, Sums> days = new TreeMap<>();
        for (Object[] row : saleRepository.sumByDayAndType(fromInstant, toInstant)) {
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
        return new SalesByDayReportResponse(start, end, months, rangeSums.toRow(null));
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("El rango del reporte no puede superar un año.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
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
