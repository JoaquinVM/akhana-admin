package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ConsumptionsReportResponse;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse.Row;
import com.akhana.akhana_admin.dto.SalesBySupplierReportResponse;
import com.akhana.akhana_admin.dto.TotalsReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse.CategoryRow;
import com.akhana.akhana_admin.repository.ConsumptionRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private ConsumptionRepository consumptionRepository;

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
    @DisplayName("POS-12, POS-13: por asignar y de terceros van aparte; terceros no suma al total de la tienda")
    void pendingAndThirdPartyApart() {
        // One sale of 100: normal 50 (cost 30), pending 20 and Mami 30
        stubRows(row("2026-07-17", "NORMAL", 1, "1.000", "100.00", "30.00", "20.00", "30.00"));

        Row day = reportService.getSalesByDay(JULY_1, AUGUST_31).months().getFirst().days().getFirst();

        assertEquals(new BigDecimal("50.00"), day.normalTotal());
        assertEquals(new BigDecimal("30.00"), day.normalCost());
        assertEquals(new BigDecimal("20.00"), day.normalProfit());
        assertEquals(new BigDecimal("20.00"), day.pendingTotal());
        assertEquals(new BigDecimal("30.00"), day.thirdPartyTotal());
        assertEquals(new BigDecimal("70.00"), day.total());
        assertEquals(new BigDecimal("20.00"), day.profit());
    }

    @Test
    @DisplayName("POS-12: sin identificar definitivo suma al total de la tienda pero no a las normales ni a la utilidad")
    void unidentifiedApartFromProfit() {
        // The same sale with the pending line of 20 closed as unidentified for good
        stubRows(row("2026-07-17", "NORMAL", 1, "1.000", "100.00", "30.00", "0", "30.00", "20.00"));
        stubConsumptions(consumption("2026-07", 1, "5.00"));

        Row day = reportService.getSalesByDay(JULY_1, AUGUST_31).months().getFirst().days().getFirst();

        assertEquals(new BigDecimal("50.00"), day.normalTotal());
        assertEquals(new BigDecimal("30.00"), day.normalCost());
        assertEquals(new BigDecimal("20.00"), day.normalProfit());
        assertEquals(new BigDecimal("40.00"), day.normalMargin());
        assertEquals(new BigDecimal("0.00"), day.pendingTotal());
        assertEquals(new BigDecimal("20.00"), day.unidentifiedTotal());
        assertEquals(new BigDecimal("30.00"), day.thirdPartyTotal());
        assertEquals(new BigDecimal("70.00"), day.total());
        assertEquals(new BigDecimal("20.00"), day.profit());

        TotalsReportResponse.Row july = reportService.getTotals(JULY_1, AUGUST_31).months().getFirst();
        assertEquals(new BigDecimal("20.00"), july.unidentifiedTotal());
        assertEquals(new BigDecimal("70.00"), july.total());
        assertEquals(new BigDecimal("20.00"), july.normalProfit());
        assertEquals(new BigDecimal("15.00"), july.profitAfterConsumptions());
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

        verify(saleRepository).sumByDayAndType(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));
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
    @DisplayName("POS-12, POS-13: las líneas por asignar y de terceros no van a ningún proveedor")
    void freeLinesOutOfSuppliers() {
        stubLines(line("v1", "2026-07", "NORMAL", "100.00", ALMA, "Alma", "1", "50.00", "30.00"),
                freeLine("v1", "2026-07", "100.00", BOHEMIA, "Bohemia", "1", "20.00", "0"),
                freeLine("v1", "2026-07", "100.00", BOHEMIA, "Bohemia", "1", "30.00", "30.00"));

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(JULY_1, AUGUST_31);

        assertEquals(1, report.suppliers().size());
        assertEquals(new BigDecimal("50.00"), report.suppliers().getFirst().totals().normalTotal());
        assertEquals(new BigDecimal("20.00"), report.totals().normalProfit());
    }

    @Test
    @DisplayName("P-44: con líneas libres el descuento general se reparte solo entre las normales")
    void generalDiscountSkipsFreeLines() {
        // 2 Roquin (8.00) + Aceite de coco (125.00) + Mermelada de Mami (30.00), general discount 5.00: total 158.00.
        // Shares 5 x 8 / 133 = 0.30 and 5 x 125 / 133 = 4.70; the Mami line keeps its 30.00
        stubLines(line("v1", "2026-07", "NORMAL", "158.00", ALMA, "Alma", "2", "8.00", "6.00"),
                freeLine("v1", "2026-07", "158.00", BOHEMIA, "Bohemia", "1", "30.00", "30.00"),
                line("v1", "2026-07", "NORMAL", "158.00", BOHEMIA, "Bohemia", "1", "125.00", "100.00"));

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(JULY_1, AUGUST_31);

        assertEquals(new BigDecimal("7.70"), report.suppliers().get(0).totals().normalTotal());
        assertEquals(new BigDecimal("120.30"), report.suppliers().get(1).totals().normalTotal());
        assertEquals(new BigDecimal("128.00"), report.totals().normalTotal());
    }

    @Test
    @DisplayName("P-44: una línea asignada cuenta en su proveedor con su monto exacto, sin descuento general")
    void assignedLineKeepsItsAmount() {
        stubLines(line("v1", "2026-07", "NORMAL", "70.00", ALMA, "Alma", "1", "60.00", "40.00"),
                assignedLine("v1", "2026-07", "70.00", BOHEMIA, "Bohemia", "1", "20.00", "12.00"));

        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(JULY_1, AUGUST_31);

        assertEquals(new BigDecimal("50.00"), report.suppliers().get(0).totals().normalTotal());
        assertEquals(new BigDecimal("20.00"), report.suppliers().get(1).totals().normalTotal());
        assertEquals(new BigDecimal("8.00"), report.suppliers().get(1).totals().normalProfit());
        assertEquals(new BigDecimal("70.00"), report.totals().normalTotal());
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
        verify(saleRepository).findReportLines(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));
        assertThrows(IllegalArgumentException.class, () -> reportService.getSalesBySupplier(AUGUST_31, JULY_1));
    }

    @Test
    @DisplayName("Agosto: Venta 67.031,50, Pedido -67.097,56 y Comida -2.530 como Resumen Transacc.; el neto los suma")
    void augustByCategoryLikeThePivotTable() {
        UUID venta = UUID.randomUUID();
        UUID pedido = UUID.randomUUID();
        UUID comida = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        // Sales of the POS and a collection from the friend store both go to Venta (FIN-07, DEU-06)
        stubCategories(
                category("2026-08", "INGRESO", venta, "Venta", "66500.00"),
                category("2026-08", "INGRESO", venta, "Venta", "531.50"),
                category("2026-08", "INGRESO", otro, "Otro", "2275.03"),
                category("2026-08", "GASTO", pedido, "Pedido", "67097.56"),
                category("2026-08", "GASTO", comida, "Comida", "2530.00"));
        stubTransfers();
        stubConsumptions();

        TransactionsByCategoryReportResponse.Month august =
                reportService.getTransactionsByCategory(JULY_1, AUGUST_31).months().getFirst();

        assertEquals("2026-08", august.month());
        assertEquals(List.of(new CategoryRow(otro, "Otro", new BigDecimal("2275.03")),
                new CategoryRow(venta, "Venta", new BigDecimal("67031.50"))), august.incomes());
        assertEquals(List.of(new CategoryRow(comida, "Comida", new BigDecimal("-2530.00")),
                new CategoryRow(pedido, "Pedido", new BigDecimal("-67097.56"))), august.expenses());
        assertEquals(new BigDecimal("69306.53"), august.incomeTotal());
        assertEquals(new BigDecimal("-69627.56"), august.expenseTotal());
        assertEquals(new BigDecimal("-321.03"), august.net());
    }

    @Test
    @DisplayName("Retiro de caja y depósito van en Transferencias por origen y destino, fuera del neto")
    void transfersApartFromTheNet() {
        UUID caja = UUID.randomUUID();
        UUID efectivo = UUID.randomUUID();
        UUID bisa = UUID.randomUUID();
        UUID venta = UUID.randomUUID();
        stubCategories(category("2026-08", "INGRESO", venta, "Venta", "100.00"));
        stubTransfers(
                transfer("2026-08", caja, "Caja", efectivo, "Efectivo", "19500.00"),
                transfer("2026-08", efectivo, "Efectivo", bisa, "Bisa", "7500.00"));
        stubConsumptions();

        TransactionsByCategoryReportResponse.Month august =
                reportService.getTransactionsByCategory(JULY_1, AUGUST_31).months().getFirst();

        assertEquals(new BigDecimal("100.00"), august.net());
        assertEquals(2, august.transfers().size());
        assertEquals("Caja", august.transfers().get(0).sourceAccountName());
        assertEquals("Efectivo", august.transfers().get(0).destinationAccountName());
        assertEquals(new BigDecimal("19500.00"), august.transfers().get(0).amount());
        assertEquals("Efectivo", august.transfers().get(1).sourceAccountName());
        assertEquals("Bisa", august.transfers().get(1).destinationAccountName());
    }

    @Test
    @DisplayName("Consumo al costo va aparte sin movimiento de dinero; un mes solo con consumos aparece; el rango suma")
    void consumptionsAtCostApartAndRangeTotals() {
        UUID venta = UUID.randomUUID();
        UUID comida = UUID.randomUUID();
        UUID caja = UUID.randomUUID();
        UUID efectivo = UUID.randomUUID();
        stubCategories(
                category("2026-08", "INGRESO", venta, "Venta", "1000.00"),
                category("2026-08", "GASTO", comida, "Comida", "200.00"),
                category("2026-09", "GASTO", null, null, "10.00"),
                category("2026-09", "GASTO", comida, "Comida", "50.00"));
        stubTransfers(transfer("2026-08", caja, "Caja", efectivo, "Efectivo", "300.00"),
                transfer("2026-09", caja, "Caja", efectivo, "Efectivo", "200.00"));
        stubConsumptions(consumption("2026-07", 2, "45.50"), consumption("2026-08", 1, "30.00"));

        TransactionsByCategoryReportResponse report = reportService.getTransactionsByCategory(JULY_1, SEPTEMBER_30);

        assertEquals(List.of("2026-07", "2026-08", "2026-09"),
                report.months().stream().map(TransactionsByCategoryReportResponse.Month::month).toList());
        TransactionsByCategoryReportResponse.Month july = report.months().getFirst();
        assertTrue(july.incomes().isEmpty());
        assertEquals(new BigDecimal("0.00"), july.net());
        assertEquals(2, july.consumptions());
        assertEquals(new BigDecimal("45.50"), july.consumptionTotal());
        assertEquals(new BigDecimal("800.00"), report.months().get(1).net());
        // Movements without a category go last
        assertEquals("Sin categoría", report.months().get(2).expenses().getLast().categoryName());

        TransactionsByCategoryReportResponse.Month totals = report.totals();
        assertNull(totals.month());
        assertEquals(new BigDecimal("740.00"), totals.net());
        assertEquals(List.of(new CategoryRow(comida, "Comida", new BigDecimal("-250.00")),
                new CategoryRow(null, "Sin categoría", new BigDecimal("-10.00"))), totals.expenses());
        assertEquals(new BigDecimal("500.00"), totals.transfers().getFirst().amount());
        assertEquals(3, totals.consumptions());
        assertEquals(new BigDecimal("75.50"), totals.consumptionTotal());
    }

    @Test
    @DisplayName("Movimientos usa el mismo rango de días de la tienda y sus validaciones")
    void transactionsReportUsesTheSameRange() {
        stubCategories();
        stubTransfers();
        stubConsumptions();

        TransactionsByCategoryReportResponse report =
                reportService.getTransactionsByCategory(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));

        assertTrue(report.months().isEmpty());
        assertEquals(new BigDecimal("0.00"), report.totals().net());
        LocalDate from = LocalDate.of(2026, 7, 17);
        LocalDate to = LocalDate.of(2026, 7, 17);
        verify(financialTransactionRepository).sumByMonthAndCategory(from, to);
        verify(financialTransactionRepository).sumTransfersByMonth(from, to);
        verify(consumptionRepository).sumByMonth(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));
        assertThrows(IllegalArgumentException.class, () -> reportService.getTransactionsByCategory(AUGUST_31, JULY_1));
    }

    @Test
    @DisplayName("Julio: utilidad 8505.78 de Resumen Ventas Diario menos consumos 300 da 8205.78")
    void julyProfitAfterConsumptions() {
        // Two July days that add up to the pivot table's July: total 37688, profit 8505.78
        stubRows(row("2026-07-17", "NORMAL", 30, "74.000", "2745.00", "2104.16"),
                row("2026-07-18", "NORMAL", 400, "1000.000", "34839.00", "26974.06"),
                row("2026-07-18", "AL_COSTO", 1, "1.000", "104.00", "104.00"));
        stubConsumptions(consumption("2026-07", 3, "300.00"));

        TotalsReportResponse report = reportService.getTotals(JULY_1, LocalDate.of(2026, 7, 31));

        TotalsReportResponse.Row july = report.months().getFirst();
        assertEquals("2026-07", july.month());
        assertEquals(430, july.normalSales());
        assertEquals(new BigDecimal("37584.00"), july.normalTotal());
        assertEquals(new BigDecimal("29078.22"), july.normalCost());
        assertEquals(new BigDecimal("8505.78"), july.normalProfit());
        assertEquals(new BigDecimal("22.63"), july.normalMargin());
        assertEquals(1, july.atCostSales());
        assertEquals(new BigDecimal("104.00"), july.atCostTotal());
        assertEquals(new BigDecimal("37688.00"), july.total());
        assertEquals(3, july.consumptions());
        assertEquals(new BigDecimal("300.00"), july.consumptionTotal());
        assertEquals(new BigDecimal("8205.78"), july.profitAfterConsumptions());
    }

    @Test
    @DisplayName("Totales: un mes solo con consumos aparece; el rango suma los meses igual que ventas por día")
    void totalsByMonthAndRange() {
        stubRows(row("2026-07-17", "NORMAL", 30, "74.000", "2745.00", "2104.16"),
                row("2026-09-02", "NORMAL", 2, "2.000", "100.00", "70.00"));
        stubConsumptions(consumption("2026-08", 1, "30.00"), consumption("2026-09", 2, "12.50"));

        TotalsReportResponse report = reportService.getTotals(JULY_1, SEPTEMBER_30);

        assertEquals(List.of("2026-07", "2026-08", "2026-09"),
                report.months().stream().map(TotalsReportResponse.Row::month).toList());
        TotalsReportResponse.Row august = report.months().get(1);
        assertEquals(0, august.normalSales());
        assertEquals(new BigDecimal("0.00"), august.normalProfit());
        assertNull(august.normalMargin());
        assertEquals(new BigDecimal("-30.00"), august.profitAfterConsumptions());
        assertEquals(new BigDecimal("640.84"), report.months().getFirst().profitAfterConsumptions());

        TotalsReportResponse.Row totals = report.totals();
        assertNull(totals.month());
        assertEquals(new BigDecimal("2845.00"), totals.normalTotal());
        assertEquals(new BigDecimal("670.84"), totals.normalProfit());
        assertEquals(3, totals.consumptions());
        assertEquals(new BigDecimal("42.50"), totals.consumptionTotal());
        assertEquals(new BigDecimal("628.34"), totals.profitAfterConsumptions());
        assertEquals(totals.normalProfit(), reportService.getSalesByDay(JULY_1, SEPTEMBER_30).totals().normalProfit());
    }

    @Test
    @DisplayName("Totales usa el mismo rango de días de la tienda y sus validaciones")
    void totalsReportUsesTheSameRange() {
        stubRows();
        stubConsumptions();

        TotalsReportResponse report = reportService.getTotals(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));

        assertTrue(report.months().isEmpty());
        assertEquals(new BigDecimal("0.00"), report.totals().profitAfterConsumptions());
        verify(saleRepository).sumByDayAndType(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));
        verify(consumptionRepository).sumByMonth(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));
        assertThrows(IllegalArgumentException.class, () -> reportService.getTotals(AUGUST_31, JULY_1));
    }

    @Test
    @DisplayName("Consumos por día y mes: cantidad de consumos, productos y total al costo, con subtotal y total")
    void consumptionsByDayAndMonth() {
        when(consumptionRepository.sumByDay(any(), any())).thenReturn(List.of(
                new Object[] {"2026-07-21", 2L, new BigDecimal("1.100"), new BigDecimal("16.00")},
                new Object[] {"2026-07-25", 1L, new BigDecimal("3.000"), new BigDecimal("30.00")},
                new Object[] {"2026-08-25", 1L, new BigDecimal("1.000"), new BigDecimal("4.50")}));
        stubConsumptionSuppliers();

        ConsumptionsReportResponse report = reportService.getConsumptions(JULY_1, AUGUST_31);

        assertEquals(List.of("2026-07", "2026-08"),
                report.months().stream().map(ConsumptionsReportResponse.Month::month).toList());
        ConsumptionsReportResponse.Month july = report.months().getFirst();
        assertEquals(LocalDate.of(2026, 7, 21), july.days().getFirst().date());
        assertEquals(new BigDecimal("1.100"), july.days().getFirst().quantity());
        assertNull(july.totals().date());
        assertEquals(3, july.totals().consumptions());
        assertEquals(new BigDecimal("4.100"), july.totals().quantity());
        assertEquals(new BigDecimal("46.00"), july.totals().total());
        assertEquals(4, report.totals().consumptions());
        assertEquals(new BigDecimal("5.100"), report.totals().quantity());
        assertEquals(new BigDecimal("50.50"), report.totals().total());
    }

    @Test
    @DisplayName("Consumos por proveedor y mes: proveedores por nombre, sin proveedor al final y total del rango")
    void consumptionsBySupplierAndMonth() {
        when(consumptionRepository.sumByDay(any(), any())).thenReturn(List.of());
        stubConsumptionSuppliers(
                supplierConsumption("2026-08", BOHEMIA, "Bohemia", "1.000", "72.00"),
                supplierConsumption("2026-07", BOHEMIA, "Bohemia", "2.000", "144.00"),
                supplierConsumption("2026-07", null, null, "0.250", "15.00"),
                supplierConsumption("2026-07", ALMA, "Alma", "0.100", "6.00"));

        ConsumptionsReportResponse report = reportService.getConsumptions(JULY_1, AUGUST_31);

        assertEquals(List.of("Alma", "Bohemia", "Sin proveedor"),
                report.suppliers().stream().map(ConsumptionsReportResponse.Supplier::supplierName).toList());
        ConsumptionsReportResponse.Supplier bohemia = report.suppliers().get(1);
        assertEquals(List.of("2026-07", "2026-08"),
                bohemia.months().stream().map(ConsumptionsReportResponse.SupplierRow::month).toList());
        assertEquals(new BigDecimal("3.000"), bohemia.totals().quantity());
        assertEquals(new BigDecimal("216.00"), bohemia.totals().total());
        assertNull(report.suppliers().get(2).supplierId());
        assertNull(report.supplierTotals().month());
        assertEquals(new BigDecimal("3.350"), report.supplierTotals().quantity());
        assertEquals(new BigDecimal("237.00"), report.supplierTotals().total());
        assertTrue(report.months().isEmpty());
        assertEquals(new BigDecimal("0.00"), report.totals().total());
    }

    @Test
    @DisplayName("Consumos usa el mismo rango de días de la tienda y sus validaciones")
    void consumptionsReportUsesTheSameRange() {
        when(consumptionRepository.sumByDay(any(), any())).thenReturn(List.of());
        stubConsumptionSuppliers();

        reportService.getConsumptions(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));

        verify(consumptionRepository).sumByDay(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));
        verify(consumptionRepository).sumBySupplierAndMonth(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 17));
        assertThrows(IllegalArgumentException.class, () -> reportService.getConsumptions(AUGUST_31, JULY_1));
        assertThrows(IllegalArgumentException.class,
                () -> reportService.getConsumptions(JULY_1, JULY_1.plusDays(366)));
    }

    private void stubConsumptionSuppliers(Object[]... rows) {
        when(consumptionRepository.sumBySupplierAndMonth(any(), any())).thenReturn(List.of(rows));
    }

    private static Object[] supplierConsumption(String month, UUID supplierId, String supplierName, String quantity,
                                                String total) {
        return new Object[] {month, supplierId, supplierName, new BigDecimal(quantity), new BigDecimal(total)};
    }

    private void stubCategories(Object[]... rows) {
        when(financialTransactionRepository.sumByMonthAndCategory(any(), any())).thenReturn(List.of(rows));
    }

    private void stubTransfers(Object[]... rows) {
        when(financialTransactionRepository.sumTransfersByMonth(any(), any())).thenReturn(List.of(rows));
    }

    private void stubConsumptions(Object[]... rows) {
        when(consumptionRepository.sumByMonth(any(), any())).thenReturn(List.of(rows));
    }

    private static Object[] category(String month, String direction, UUID id, String name, String amount) {
        return new Object[] {month, direction, id, name, new BigDecimal(amount)};
    }

    private static Object[] transfer(String month, UUID source, String sourceName, UUID destination,
                                     String destinationName, String amount) {
        return new Object[] {month, source, sourceName, destination, destinationName, new BigDecimal(amount)};
    }

    private static Object[] consumption(String month, long consumptions, String total) {
        return new Object[] {month, consumptions, new BigDecimal(total)};
    }

    private void stubLines(Object[]... lines) {
        when(saleRepository.findReportLines(any(), any())).thenReturn(List.of(lines));
    }

    private static Object[] line(String sale, String month, String type, String saleTotal, UUID supplierId,
                                 String supplierName, String quantity, String subtotal, String cost) {
        return new Object[] {sale, month, type, new BigDecimal(saleTotal), supplierId, supplierName,
                new BigDecimal(quantity), new BigDecimal(subtotal), new BigDecimal(cost), true, false};
    }

    // POS-12, POS-13: a pending or third-party line, left out of the suppliers
    private static Object[] freeLine(String sale, String month, String saleTotal, UUID supplierId, String supplierName,
                                     String quantity, String subtotal, String cost) {
        return new Object[] {sale, month, "NORMAL", new BigDecimal(saleTotal), supplierId, supplierName,
                new BigDecimal(quantity), new BigDecimal(subtotal), new BigDecimal(cost), false, true};
    }

    // POS-12: a free line already assigned to a product, counted in its supplier
    private static Object[] assignedLine(String sale, String month, String saleTotal, UUID supplierId,
                                         String supplierName, String quantity, String subtotal, String cost) {
        return new Object[] {sale, month, "NORMAL", new BigDecimal(saleTotal), supplierId, supplierName,
                new BigDecimal(quantity), new BigDecimal(subtotal), new BigDecimal(cost), true, true};
    }

    private void stubRows(Object[]... rows) {
        when(saleRepository.sumByDayAndType(any(), any())).thenReturn(List.of(rows));
    }

    private static Object[] row(String day, String type, long sales, String quantity, String total, String cost) {
        return row(day, type, sales, quantity, total, cost, "0", "0");
    }

    private static Object[] row(String day, String type, long sales, String quantity, String total, String cost,
                                String pending, String thirdParty) {
        return row(day, type, sales, quantity, total, cost, pending, thirdParty, "0");
    }

    private static Object[] row(String day, String type, long sales, String quantity, String total, String cost,
                                String pending, String thirdParty, String unidentified) {
        return new Object[] {day, type, sales, new BigDecimal(quantity), new BigDecimal(total), new BigDecimal(cost),
                new BigDecimal(pending), new BigDecimal(thirdParty), new BigDecimal(unidentified)};
    }
}
