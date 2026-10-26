package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse.Row;
import com.akhana.akhana_admin.dto.SalesBySupplierReportResponse;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Sales per day and per supplier (FIN-10) with normal and at-cost sales apart (CON-03); scenarios of specs/reports.md
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final LocalDate JULY_1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate AUGUST_31 = LocalDate.of(2026, 8, 31);
    private static final LocalDate SEPTEMBER_30 = LocalDate.of(2026, 9, 30);
    private static final UUID BOHEMIA = UUID.randomUUID();
    private static final UUID ALMA = UUID.randomUUID();

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

    @Test
    @DisplayName("Bohemia jul–sep como Resumen Ventas: 2/160/16, 2/160/16, 3/230/28 y total 7/550/60")
    void bohemiaByMonthLikeTheSpreadsheet() {
        // The seven Bohemia rows of the Ventas sheet: Catuai 80 (cost 72) on 28-07, 30-07, 18-08 (x2) and 25-09,
        // Blend 75 (cost 65) twice on 29-09
        stubLines(line("j1", "2026-07", "NORMAL", "80.00", BOHEMIA, "Bohemia", "1", "80.00", "72.00"),
                line("j2", "2026-07", "NORMAL", "80.00", BOHEMIA, "Bohemia", "1", "80.00", "72.00"),
                line("a1", "2026-08", "NORMAL", "160.00", BOHEMIA, "Bohemia", "2", "160.00", "144.00"),
                line("s1", "2026-09", "NORMAL", "80.00", BOHEMIA, "Bohemia", "1", "80.00", "72.00"),
                line("s2", "2026-09", "NORMAL", "75.00", BOHEMIA, "Bohemia", "1", "75.00", "65.00"),
                line("s3", "2026-09", "NORMAL", "75.00", BOHEMIA, "Bohemia", "1", "75.00", "65.00"));

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(JULY_1, SEPTEMBER_30);

        SalesBySupplierReportResponse.Supplier bohemia = report.suppliers().getFirst();
        assertEquals(BOHEMIA, bohemia.supplierId());
        assertEquals("Bohemia", bohemia.supplierName());
        assertEquals(List.of("2026-07 2.000 160.00 16.00", "2026-08 2.000 160.00 16.00", "2026-09 3.000 230.00 28.00"),
                bohemia.months().stream()
                        .map(m -> m.month() + " " + m.normalQuantity() + " " + m.normalTotal() + " " + m.normalProfit())
                        .toList());
        assertNull(bohemia.totals().month());
        assertEquals(new BigDecimal("7.000"), bohemia.totals().normalQuantity());
        assertEquals(new BigDecimal("550.00"), bohemia.totals().normalTotal());
        assertEquals(new BigDecimal("490.00"), bohemia.totals().normalCost());
        assertEquals(new BigDecimal("60.00"), bohemia.totals().normalProfit());
        assertEquals(new BigDecimal("10.91"), bohemia.totals().normalMargin());
    }

    @Test
    @DisplayName("El descuento general se reparte por subtotal y la suma de proveedores es el total de la venta")
    void generalDiscountSpreadBySubtotal() {
        // Subtotals 20 + 20 + 10 with a sale total of 49.99: shares 0.40 / 0.40 / 0.20 of 0.01 round to 0.00,
        // so the 0.01 remainder goes to the first largest line (Alma)
        stubLines(line("v1", "2026-07", "NORMAL", "49.99", ALMA, "Alma", "2", "20.00", "12.00"),
                line("v1", "2026-07", "NORMAL", "49.99", BOHEMIA, "Bohemia", "2", "20.00", "12.00"),
                line("v1", "2026-07", "NORMAL", "49.99", BOHEMIA, "Bohemia", "1", "10.00", "6.00"));

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(JULY_1, AUGUST_31);

        assertEquals(List.of("Alma", "Bohemia"), report.suppliers().stream().map(SalesBySupplierReportResponse.Supplier::supplierName).toList());
        assertEquals(new BigDecimal("19.99"), report.suppliers().get(0).totals().normalTotal());
        assertEquals(new BigDecimal("30.00"), report.suppliers().get(1).totals().normalTotal());
        assertEquals(new BigDecimal("49.99"), report.totals().normalTotal());
    }

    @Test
    @DisplayName("Un descuento general de 5 en 20 + 0,250 kg × 80 se reparte 2,50 y 2,50; el costo no cambia")
    void generalDiscountKeepsTheCost() {
        stubLines(line("v1", "2026-07", "NORMAL", "35.00", ALMA, "Alma", "2", "20.00", "12.00"),
                line("v1", "2026-07", "NORMAL", "35.00", BOHEMIA, "Bohemia", "0.250", "20.00", "15.00"));

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(JULY_1, AUGUST_31);

        SalesBySupplierReportResponse.Row alma = report.suppliers().get(0).totals();
        SalesBySupplierReportResponse.Row bohemia = report.suppliers().get(1).totals();
        assertEquals(new BigDecimal("17.50"), alma.normalTotal());
        assertEquals(new BigDecimal("5.50"), alma.normalProfit());
        assertEquals(new BigDecimal("17.50"), bohemia.normalTotal());
        assertEquals(new BigDecimal("0.250"), bohemia.normalQuantity());
        assertEquals(new BigDecimal("2.50"), bohemia.normalProfit());
        assertEquals(new BigDecimal("35.00"), report.totals().total());
        assertEquals(new BigDecimal("8.00"), report.totals().normalProfit());
    }

    @Test
    @DisplayName("Las ventas al costo van aparte y no bajan el margen del proveedor")
    void atCostSalesDoNotLowerTheMargin() {
        stubLines(line("n1", "2026-07", "NORMAL", "100.00", BOHEMIA, "Bohemia", "1", "100.00", "60.00"),
                line("c1", "2026-07", "AL_COSTO", "104.00", BOHEMIA, "Bohemia", "1", "104.00", "104.00"));

        SalesBySupplierReportResponse.Row july = reportService.getSalesBySupplier(JULY_1, AUGUST_31)
                .suppliers().getFirst().months().getFirst();

        assertEquals(new BigDecimal("40.00"), july.normalProfit());
        assertEquals(new BigDecimal("40.00"), july.normalMargin());
        assertEquals(new BigDecimal("1.000"), july.atCostQuantity());
        assertEquals(new BigDecimal("104.00"), july.atCostTotal());
        assertEquals(new BigDecimal("204.00"), july.total());
    }

    @Test
    @DisplayName("Las líneas históricas sin proveedor van al final en «Sin proveedor»")
    void linesWithoutSupplierGoLast() {
        stubLines(line("h1", "2026-07", "NORMAL", "10.00", null, null, "1", "10.00", "0.00"),
                line("n1", "2026-07", "NORMAL", "10.00", BOHEMIA, "bohemia", "1", "10.00", "5.00"),
                line("n2", "2026-07", "NORMAL", "10.00", ALMA, "Alma", "1", "10.00", "5.00"));

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(JULY_1, AUGUST_31);

        assertEquals(List.of("Alma", "bohemia", "Sin proveedor"),
                report.suppliers().stream().map(SalesBySupplierReportResponse.Supplier::supplierName).toList());
        assertNull(report.suppliers().getLast().supplierId());
        assertEquals(new BigDecimal("30.00"), report.totals().total());
    }

    @Test
    @DisplayName("Proveedores usa el mismo rango de días de la tienda y sus validaciones")
    void supplierReportUsesTheSameRange() {
        stubLines();

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));

        assertTrue(report.suppliers().isEmpty());
        assertNull(report.totals().normalMargin());
        verify(saleRepository).findReportLines(Instant.parse("2026-07-17T04:00:00Z"), Instant.parse("2026-07-18T04:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> reportService.getSalesBySupplier(AUGUST_31, JULY_1));
    }

    private void stubLines(Object[]... lines) {
        when(saleRepository.findReportLines(any(), any())).thenReturn(List.of(lines));
    }

    private static Object[] line(String sale, String month, String type, String saleTotal, UUID supplierId,
                                 String supplierName, String quantity, String subtotal, String cost) {
        return new Object[] {sale, month, type, new BigDecimal(saleTotal), supplierId, supplierName,
                new BigDecimal(quantity), new BigDecimal(subtotal), new BigDecimal(cost)};
    }

    private void stubRows(Object[]... rows) {
        when(saleRepository.sumByDayAndType(any(), any())).thenReturn(List.of(rows));
    }

    private static Object[] row(String day, String type, long sales, String quantity, String total, String cost) {
        return new Object[] {day, type, sales, new BigDecimal(quantity), new BigDecimal(total), new BigDecimal(cost)};
    }
}
