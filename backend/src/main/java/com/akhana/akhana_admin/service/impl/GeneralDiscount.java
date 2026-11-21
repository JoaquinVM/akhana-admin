package com.akhana.akhana_admin.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * How a sale's general discount reaches its lines (P-44), for the report per supplier (FIN-10) and the dashboard's
 * products and suppliers (DSH-03), so both always split a sale the same way. Works on the rows of
 * {@code SaleRepository.findReportLines}.
 */
final class GeneralDiscount {

    static final int SALE_ID = 0;
    static final int MONTH = 1;
    static final int SALE_TYPE = 2;
    static final int SALE_TOTAL = 3;
    static final int SUPPLIER_ID = 4;
    static final int SUPPLIER_NAME = 5;
    static final int QUANTITY = 6;
    static final int SUBTOTAL = 7;
    static final int COST = 8;
    static final int NORMAL_LINE = 9;
    static final int FREE_LINE = 10;
    static final int PRODUCT_ID = 11;
    static final int PRODUCT_NAME = 12;

    private GeneralDiscount() {
    }

    /**
     * Hands every line, sale by sale, with its total after the sale's general discount. Free lines (POS-11) keep
     * their exact amount; the discount (the other lines' subtotals minus what is left of the sale total) goes only
     * to the other lines.
     */
    static void forEachLine(List<Object[]> lines, BiConsumer<Object[], BigDecimal> action) {
        Map<Object, List<Object[]>> sales = new LinkedHashMap<>();
        for (Object[] line : lines) {
            sales.computeIfAbsent(line[SALE_ID], id -> new ArrayList<>()).add(line);
        }
        for (List<Object[]> saleLines : sales.values()) {
            BigDecimal freeTotal = saleLines.stream().filter(GeneralDiscount::isFree)
                .map(line -> (BigDecimal) line[SUBTOTAL]).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<BigDecimal> discounted = lineTotals(
                saleLines.stream().filter(line -> !isFree(line)).map(line -> (BigDecimal) line[SUBTOTAL]).toList(),
                ((BigDecimal) saleLines.getFirst()[SALE_TOTAL]).subtract(freeTotal));
            int next = 0;
            for (Object[] line : saleLines) {
                action.accept(line, isFree(line) ? (BigDecimal) line[SUBTOTAL] : discounted.get(next++));
            }
        }
    }

    /**
     * Spreads a sale's general discount (line subtotals - sale total) over its lines in proportion to their
     * subtotal. The rounding remainder goes to the line with the largest subtotal (the first one on a tie), so the
     * lines always add up to the sale total and the suppliers of a day add up to that day's sales.
     */
    static List<BigDecimal> lineTotals(List<BigDecimal> subtotals, BigDecimal saleTotal) {
        BigDecimal sum = subtotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = sum.subtract(saleTotal);
        if (discount.signum() == 0 || sum.signum() == 0) {
            return subtotals.stream().map(GeneralDiscount::money).toList();
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

    private static boolean isFree(Object[] line) {
        return Boolean.TRUE.equals(line[FREE_LINE]);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
