package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse.Row;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Sales per day (FIN-10) with normal and at-cost sales apart (CON-03); scenarios of specs/reports.md
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final LocalDate JULY_1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate AUGUST_31 = LocalDate.of(2026, 8, 31);

    @Mock
    private SaleRepository saleRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Test
    @DisplayName("17-07: total 2745, costo 2104.16, utilidad 640.84 y margen 23.35 % como la planilla")
    void normalSalesOfOneDay() {
        stubRows(row("2026-07-17", "NORMAL", 30, "74.000", "2745.00", "2104.16"));

        SalesByDayReportResponse report = reportService.getSalesByDay(JULY_1, AUGUST_31);

        Row day = report.months().getFirst().days().getFirst();
        assertEquals(LocalDate.of(2026, 7, 17), day.date());
        assertEquals(30, day.normalSales());
        assertEquals(new BigDecimal("74.000"), day.normalQuantity());
        assertEquals(new BigDecimal("2745.00"), day.normalTotal());
        assertEquals(new BigDecimal("2104.16"), day.normalCost());
        assertEquals(new BigDecimal("640.84"), day.normalProfit());
        assertEquals(new BigDecimal("23.35"), day.normalMargin());
        assertEquals(new BigDecimal("2745.00"), day.total());
        assertEquals(new BigDecimal("640.84"), day.profit());
    }

    @Test
    @DisplayName("La venta al costo va aparte: suma al total del día pero no cambia utilidad ni margen")
    void atCostSalesInTheirOwnColumns() {
        stubRows(row("2026-07-17", "NORMAL", 2, "3.000", "100.00", "60.00"),
                row("2026-07-17", "AL_COSTO", 1, "1.000", "104.00", "104.00"));

        Row day = reportService.getSalesByDay(JULY_1, AUGUST_31).months().getFirst().days().getFirst();

        assertEquals(new BigDecimal("40.00"), day.normalProfit());
        assertEquals(new BigDecimal("40.00"), day.normalMargin());
        assertEquals(1, day.atCostSales());
        assertEquals(new BigDecimal("104.00"), day.atCostTotal());
        assertEquals(new BigDecimal("204.00"), day.total());
        assertEquals(new BigDecimal("40.00"), day.profit());
    }

    @Test
    @DisplayName("Un día solo con ventas al costo deja el margen vacío")
    void marginIsEmptyWithoutNormalSales() {
        stubRows(row("2026-07-17", "AL_COSTO", 1, "1.000", "104.00", "104.00"));

        Row day = reportService.getSalesByDay(JULY_1, AUGUST_31).months().getFirst().days().getFirst();

        assertNull(day.normalMargin());
        assertEquals(new BigDecimal("0.00"), day.normalProfit());
        assertEquals(new BigDecimal("104.00"), day.total());
    }

    @Test
    @DisplayName("Agrupa por mes con subtotales y total que suman los días ya redondeados")
    void monthSubtotalsAddTheRoundedDays() {
        // Unrounded costs 0.125 + 0.125: each day rounds to 0.13, so the month shows 0.26 and not 0.25
        stubRows(row("2026-07-17", "NORMAL", 1, "1.000", "1.00", "0.125"),
                row("2026-07-18", "NORMAL", 1, "1.000", "1.00", "0.125"),
                row("2026-08-03", "NORMAL", 1, "0.250", "35.00", "27.00"));

        SalesByDayReportResponse report = reportService.getSalesByDay(JULY_1, AUGUST_31);

        assertEquals(List.of("2026-07", "2026-08"), report.months().stream().map(SalesByDayReportResponse.Month::month).toList());
        Row july = report.months().getFirst().totals();
        assertNull(july.date());
        assertEquals(2, july.normalSales());
        assertEquals(new BigDecimal("2.00"), july.normalTotal());
        assertEquals(new BigDecimal("0.26"), july.normalCost());
        assertEquals(new BigDecimal("1.74"), july.normalProfit());
        assertEquals(new BigDecimal("37.00"), report.totals().normalTotal());
        assertEquals(new BigDecimal("27.26"), report.totals().normalCost());
        assertEquals(new BigDecimal("2.250"), report.totals().normalQuantity());
        assertEquals(3, report.totals().normalSales());
    }

    @Test
    @DisplayName("Sin ventas no hay meses y el total queda en cero")
    void emptyRange() {
        stubRows();

        SalesByDayReportResponse report = reportService.getSalesByDay(JULY_1, AUGUST_31);

        assertTrue(report.months().isEmpty());
        assertEquals(new BigDecimal("0.00"), report.totals().total());
        assertNull(report.totals().normalMargin());
    }

    @Test
    @DisplayName("Los días son de la tienda: desde las 00:00 de Bolivia hasta el fin del día hasta")
    void rangeUsesStoreDays() {
        stubRows();

        reportService.getSalesByDay(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));

        verify(saleRepository).sumByDayAndType(Instant.parse("2026-07-17T04:00:00Z"), Instant.parse("2026-07-18T04:00:00Z"));
    }

    @Test
    @DisplayName("Sin fechas propone el mes en curso hasta hoy")
    void defaultsToThisMonth() {
        stubRows();

        SalesByDayReportResponse report = reportService.getSalesByDay(null, null);

        assertEquals(1, report.from().getDayOfMonth());
        assertEquals(report.to().withDayOfMonth(1), report.from());
    }

    @Test
    @DisplayName("Rechaza desde posterior a hasta y rangos de más de un año")
    void rejectsInvalidRanges() {
        IllegalArgumentException reversed = assertThrows(IllegalArgumentException.class,
                () -> reportService.getSalesByDay(AUGUST_31, JULY_1));
        assertEquals("La fecha desde no puede ser posterior a la fecha hasta.", reversed.getMessage());

        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class,
                () -> reportService.getSalesByDay(JULY_1, JULY_1.plusDays(366)));
        assertEquals("El rango del reporte no puede superar un año.", tooLong.getMessage());

        stubRows();
        assertDoesNotThrow(() -> reportService.getSalesByDay(JULY_1, JULY_1.plusDays(365)));
        verify(saleRepository, times(1)).sumByDayAndType(any(), any());
    }

    private void stubRows(Object[]... rows) {
        when(saleRepository.sumByDayAndType(any(), any())).thenReturn(List.of(rows));
    }

    private static Object[] row(String day, String type, long sales, String quantity, String total, String cost) {
        return new Object[] {day, type, sales, new BigDecimal(quantity), new BigDecimal(total), new BigDecimal(cost)};
    }
}
