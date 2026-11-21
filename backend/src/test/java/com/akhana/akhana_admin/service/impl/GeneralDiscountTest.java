package com.akhana.akhana_admin.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// How a sale's general discount reaches its lines (P-44), shared by the report per supplier and the dashboard
class GeneralDiscountTest {

    @Test
    @DisplayName("Sin descuento general cada línea queda con su subtotal")
    void withoutDiscountKeepsTheSubtotals() {
        assertEquals(List.of(money("20.00"), money("15.00")),
                GeneralDiscount.lineTotals(List.of(money("20"), money("15")), money("35.00")));
    }

    @Test
    @DisplayName("El descuento se reparte en proporción al subtotal y las líneas suman el total de la venta")
    void spreadsInProportion() {
        // 1.67 over 35: 0.95 to the line of 20 and 0.72 to the one of 15
        List<BigDecimal> totals = GeneralDiscount.lineTotals(List.of(money("20.00"), money("15.00")), money("33.33"));

        assertEquals(List.of(money("19.05"), money("14.28")), totals);
        assertEquals(money("33.33"), sum(totals));
    }

    @Test
    @DisplayName("El resto de centavos va a la línea de mayor subtotal; en un empate, a la primera")
    void remainderGoesToTheLargestLine() {
        // 10 over three lines of 10: 3.33 each leaves 0.01, taken by the first line
        List<BigDecimal> tie = GeneralDiscount.lineTotals(
                List.of(money("10.00"), money("10.00"), money("10.00")), money("20.00"));
        assertEquals(List.of(money("6.66"), money("6.67"), money("6.67")), tie);
        assertEquals(money("20.00"), sum(tie));

        // 0.10 over 1, 1 and 4: 0.02, 0.02 and 0.07 spread 0.11, so the line of 4 gives back the extra cent
        List<BigDecimal> largest = GeneralDiscount.lineTotals(
                List.of(money("1.00"), money("1.00"), money("4.00")), money("5.90"));
        assertEquals(List.of(money("0.98"), money("0.98"), money("3.94")), largest);
        assertEquals(money("5.90"), sum(largest));
    }

    @Test
    @DisplayName("Una venta solo con líneas libres no reparte nada")
    void nothingToSpreadOver() {
        assertEquals(List.of(), GeneralDiscount.lineTotals(List.of(), money("15.00")));
    }

    @Test
    @DisplayName("Venta por venta, las líneas libres mantienen su monto y el descuento va a las demás")
    void forEachLineKeepsFreeLinesWhole() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // Sale A of 40.33: 20 + 15 with a general discount of 1.67 and Mami's 7 kept whole; sale B without one.
        // The lines of a sale need not come together
        List<Object[]> lines = List.of(
                line(a, "40.33", "20.00", false),
                line(b, "12.00", "12.00", false),
                line(a, "40.33", "7.00", true),
                line(a, "40.33", "15.00", false));
        List<String> seen = new ArrayList<>();

        GeneralDiscount.forEachLine(lines, (line, total) ->
                seen.add((line[GeneralDiscount.SALE_ID] == a ? "A " : "B ") + total));

        assertEquals(List.of("A 19.05", "A 7.00", "A 14.28", "B 12.00"), seen);
    }

    private static Object[] line(UUID sale, String saleTotal, String subtotal, boolean free) {
        Object[] line = new Object[13];
        line[GeneralDiscount.SALE_ID] = sale;
        line[GeneralDiscount.SALE_TOTAL] = money(saleTotal);
        line[GeneralDiscount.SUBTOTAL] = money(subtotal);
        line[GeneralDiscount.FREE_LINE] = free;
        return line;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
