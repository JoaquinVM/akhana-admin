package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ConsumptionsReportResponse;
import com.akhana.akhana_admin.dto.ReportFile;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesBySupplierReportResponse;
import com.akhana.akhana_admin.dto.TotalsReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse.CategoryRow;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse.TransferRow;
import com.akhana.akhana_admin.service.impl.ReportExportServiceImpl;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// FIN-10: each report exported to Excel with the screen's range, columns and rows; scenarios of specs/reports.md
@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    private static final LocalDate JULY_1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate JULY_17 = LocalDate.of(2026, 7, 17);
    private static final LocalDate AUGUST_31 = LocalDate.of(2026, 8, 31);
    // Title, blank row, then the headers
    private static final int FIRST_ROW_WITHOUT_GROUPS = 3;
    private static final int FIRST_ROW_WITH_GROUPS = 4;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportExportServiceImpl exportService;

    @Test
    @DisplayName("Ventas por día: nombre del archivo, días, subtotal por mes y total con 2 decimales y cantidades con 3")
    void salesByDay() throws IOException {
        SalesByDayReportResponse.Row day = salesRow(JULY_17, "2745.00", "2104.16", "640.84", "23.35");
        SalesByDayReportResponse.Row july = salesRow(null, "2745.00", "2104.16", "640.84", "23.35");
        when(reportService.getSalesByDay(JULY_1, AUGUST_31)).thenReturn(new SalesByDayReportResponse(JULY_1, AUGUST_31,
                List.of(new SalesByDayReportResponse.Month("2026-07", List.of(day), july)), july));

        ReportFile file = exportService.exportSalesByDay(JULY_1, AUGUST_31);

        assertEquals("akhana-sales-by-day-2026-07-01-2026-08-31.xlsx", file.fileName());
        try (Workbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Ventas por día del 01/07/2026 al 31/08/2026", text(sheet, 0, 0));
            assertEquals("Ventas normales", text(sheet, 2, 1));
            assertEquals("Ventas al costo", text(sheet, 2, 7));
            assertEquals("Margen", text(sheet, 3, 6));

            Row dayRow = sheet.getRow(FIRST_ROW_WITH_GROUPS);
            assertEquals(JULY_17, dayRow.getCell(0).getLocalDateTimeCellValue().toLocalDate());
            assertEquals(30, dayRow.getCell(1).getNumericCellValue());
            assertNumber(dayRow.getCell(2), 74.25, "#,##0.000");
            assertNumber(dayRow.getCell(3), 2745.00, "#,##0.00");
            assertNumber(dayRow.getCell(5), 640.84, "#,##0.00");
            assertNumber(dayRow.getCell(6), 0.2335, "0.00%");
            assertNumber(dayRow.getCell(9), 104.00, "#,##0.00");
            assertNumber(dayRow.getCell(12), 2849.00, "#,##0.00");

            assertEquals("Total julio 2026", text(sheet, FIRST_ROW_WITH_GROUPS + 1, 0));
            assertTrue(isBold(workbook, sheet.getRow(FIRST_ROW_WITH_GROUPS + 1).getCell(3)));
            assertEquals("Total del rango", text(sheet, FIRST_ROW_WITH_GROUPS + 2, 0));
            assertEquals(2745.00, sheet.getRow(FIRST_ROW_WITH_GROUPS + 2).getCell(3).getNumericCellValue());
            assertEquals(FIRST_ROW_WITH_GROUPS + 2, sheet.getLastRowNum());
        }
    }

    @Test
    @DisplayName("Ventas por día: un margen vacío (sin ventas normales) queda como celda vacía")
    void salesByDay_emptyMargin() throws IOException {
        SalesByDayReportResponse.Row day = new SalesByDayReportResponse.Row(JULY_17, 0, BigDecimal.ZERO,
                money("0"), money("0"), money("0"), null, 1, new BigDecimal("1.000"), money("104"), money("0"), money("0"), money("104"),
                money("0"));
        when(reportService.getSalesByDay(null, null)).thenReturn(new SalesByDayReportResponse(JULY_1, JULY_17,
                List.of(new SalesByDayReportResponse.Month("2026-07", List.of(day), day)), day));

        ReportFile file = exportService.exportSalesByDay(null, null);

        assertEquals("akhana-sales-by-day-2026-07-01-2026-07-17.xlsx", file.fileName());
        try (Workbook workbook = open(file)) {
            assertEquals(CellType.BLANK, workbook.getSheetAt(0).getRow(FIRST_ROW_WITH_GROUPS).getCell(6).getCellType());
        }
    }

    @Test
    @DisplayName("Ventas por proveedor: el proveedor, sus meses, su total y el total del rango")
    void salesBySupplier() throws IOException {
        SalesBySupplierReportResponse.Row july = supplierRow("2026-07", "2.000", "160.00");
        SalesBySupplierReportResponse.Row total = supplierRow(null, "2.000", "160.00");
        when(reportService.getSalesBySupplier(JULY_1, AUGUST_31)).thenReturn(new SalesBySupplierReportResponse(
                JULY_1, AUGUST_31,
                List.of(new SalesBySupplierReportResponse.Supplier(UUID.randomUUID(), "Bohemia", List.of(july), total)),
                total));

        ReportFile file = exportService.exportSalesBySupplier(JULY_1, AUGUST_31);

        assertEquals("akhana-sales-by-supplier-2026-07-01-2026-08-31.xlsx", file.fileName());
        try (Workbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Bohemia", text(sheet, FIRST_ROW_WITH_GROUPS, 0));
            assertEquals("  julio 2026", text(sheet, FIRST_ROW_WITH_GROUPS + 1, 0));
            assertNumber(sheet.getRow(FIRST_ROW_WITH_GROUPS + 1).getCell(1), 2, "#,##0.000");
            assertNumber(sheet.getRow(FIRST_ROW_WITH_GROUPS + 1).getCell(2), 160, "#,##0.00");
            assertEquals("Total Bohemia", text(sheet, FIRST_ROW_WITH_GROUPS + 2, 0));
            assertEquals("Total del rango", text(sheet, FIRST_ROW_WITH_GROUPS + 3, 0));
        }
    }

    @Test
    @DisplayName("Movimientos por categoría: una columna por mes y el total; transferencias y consumo aparte")
    void transactionsByCategory() throws IOException {
        UUID venta = UUID.randomUUID();
        UUID comida = UUID.randomUUID();
        UUID caja = UUID.randomUUID();
        UUID efectivo = UUID.randomUUID();
        TransactionsByCategoryReportResponse.Month july = categoryMonth("2026-07",
                List.of(new CategoryRow(venta, "Venta", money("1000"))), List.of(), money("1000"), money("0"),
                List.of(), 0, money("0"));
        TransactionsByCategoryReportResponse.Month august = categoryMonth("2026-08",
                List.of(new CategoryRow(venta, "Venta", money("67031.50"))),
                List.of(new CategoryRow(comida, "Comida", money("-2530"))), money("67031.50"), money("-2530"),
                List.of(new TransferRow(caja, "Caja", efectivo, "Efectivo", money("19500"))), 2, money("30"));
        TransactionsByCategoryReportResponse.Month totals = categoryMonth(null,
                List.of(new CategoryRow(venta, "Venta", money("68031.50"))),
                List.of(new CategoryRow(comida, "Comida", money("-2530"))), money("68031.50"), money("-2530"),
                List.of(new TransferRow(caja, "Caja", efectivo, "Efectivo", money("19500"))), 2, money("30"));
        when(reportService.getTransactionsByCategory(JULY_1, AUGUST_31)).thenReturn(
                new TransactionsByCategoryReportResponse(JULY_1, AUGUST_31, List.of(july, august), totals));

        ReportFile file = exportService.exportTransactionsByCategory(JULY_1, AUGUST_31);

        assertEquals("akhana-transactions-by-category-2026-07-01-2026-08-31.xlsx", file.fileName());
        try (Workbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("julio 2026", text(sheet, 2, 1));
            assertEquals("agosto 2026", text(sheet, 2, 2));
            assertEquals("Total del rango", text(sheet, 2, 3));

            int row = FIRST_ROW_WITHOUT_GROUPS;
            assertEquals("Ingresos", text(sheet, row++, 0));
            assertEquals("  Venta", text(sheet, row, 0));
            assertNumber(sheet.getRow(row).getCell(2), 67031.50, "#,##0.00");
            assertNumber(sheet.getRow(row++).getCell(3), 68031.50, "#,##0.00");
            assertEquals("Total ingresos", text(sheet, row++, 0));
            assertEquals("Gastos", text(sheet, row++, 0));
            assertEquals("  Comida", text(sheet, row, 0));
            // July had no Comida: empty cell, like the screen's "—"
            assertEquals(CellType.BLANK, sheet.getRow(row).getCell(1).getCellType());
            assertEquals(-2530, sheet.getRow(row++).getCell(2).getNumericCellValue());
            assertEquals("Total gastos", text(sheet, row++, 0));
            assertEquals("Neto", text(sheet, row, 0));
            assertEquals(64501.50, sheet.getRow(row++).getCell(2).getNumericCellValue());
            assertEquals("Transferencias (entre cuentas, no suman en el neto)", text(sheet, row++, 0));
            assertEquals("  Caja → Efectivo", text(sheet, row, 0));
            assertEquals(19500, sheet.getRow(row++).getCell(2).getNumericCellValue());
            assertEquals("Consumo al costo (sin movimiento de dinero)", text(sheet, row, 0));
            assertEquals(CellType.BLANK, sheet.getRow(row).getCell(1).getCellType());
            assertEquals(-30, sheet.getRow(row).getCell(2).getNumericCellValue());
            assertEquals(row, sheet.getLastRowNum());
        }
    }

    @Test
    @DisplayName("Totales: una fila por mes con la utilidad menos consumos y el total del rango")
    void totals() throws IOException {
        TotalsReportResponse.Row july = new TotalsReportResponse.Row("2026-07", 120, money("37584"), money("29078.22"),
                money("8505.78"), money("22.63"), 1, money("104"), money("0"), money("0"), money("37688"), 3, money("300"), money("8205.78"));
        TotalsReportResponse.Row total = new TotalsReportResponse.Row(null, 120, money("37584"), money("29078.22"),
                money("8505.78"), money("22.63"), 1, money("104"), money("0"), money("0"), money("37688"), 3, money("300"), money("8205.78"));
        when(reportService.getTotals(JULY_1, AUGUST_31))
                .thenReturn(new TotalsReportResponse(JULY_1, AUGUST_31, List.of(july), total));

        ReportFile file = exportService.exportTotals(JULY_1, AUGUST_31);

        assertEquals("akhana-totals-2026-07-01-2026-08-31.xlsx", file.fileName());
        try (Workbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Utilidad menos consumos", text(sheet, 3, 13));
            assertEquals("julio 2026", text(sheet, FIRST_ROW_WITH_GROUPS, 0));
            assertNumber(sheet.getRow(FIRST_ROW_WITH_GROUPS).getCell(4), 8505.78, "#,##0.00");
            assertNumber(sheet.getRow(FIRST_ROW_WITH_GROUPS).getCell(13), 8205.78, "#,##0.00");
            assertEquals("Total del rango", text(sheet, FIRST_ROW_WITH_GROUPS + 1, 0));
        }
    }

    @Test
    @DisplayName("Consumos: una hoja por día y otra por proveedor y mes, con el mismo total")
    void consumptions() throws IOException {
        ConsumptionsReportResponse.DayRow day = new ConsumptionsReportResponse.DayRow(JULY_17, 2,
                new BigDecimal("1.250"), money("45.50"));
        ConsumptionsReportResponse.DayRow sum = new ConsumptionsReportResponse.DayRow(null, 2,
                new BigDecimal("1.250"), money("45.50"));
        ConsumptionsReportResponse.SupplierRow month = new ConsumptionsReportResponse.SupplierRow("2026-07",
                new BigDecimal("1.250"), money("45.50"));
        ConsumptionsReportResponse.SupplierRow supplierSum = new ConsumptionsReportResponse.SupplierRow(null,
                new BigDecimal("1.250"), money("45.50"));
        when(reportService.getConsumptions(JULY_1, AUGUST_31)).thenReturn(new ConsumptionsReportResponse(JULY_1,
                AUGUST_31, List.of(new ConsumptionsReportResponse.Month("2026-07", List.of(day), sum)), sum,
                List.of(new ConsumptionsReportResponse.Supplier(null, "Sin proveedor", List.of(month), supplierSum)),
                supplierSum));

        ReportFile file = exportService.exportConsumptions(JULY_1, AUGUST_31);

        assertEquals("akhana-consumptions-2026-07-01-2026-08-31.xlsx", file.fileName());
        try (Workbook workbook = open(file)) {
            Sheet days = workbook.getSheet("Por día");
            assertNumber(days.getRow(FIRST_ROW_WITHOUT_GROUPS).getCell(2), 1.25, "#,##0.000");
            assertNumber(days.getRow(FIRST_ROW_WITHOUT_GROUPS).getCell(3), 45.50, "#,##0.00");
            assertEquals("Total julio 2026", text(days, FIRST_ROW_WITHOUT_GROUPS + 1, 0));
            assertEquals("Total del rango", text(days, FIRST_ROW_WITHOUT_GROUPS + 2, 0));

            Sheet suppliers = workbook.getSheet("Por proveedor y mes");
            assertEquals("Sin proveedor", text(suppliers, FIRST_ROW_WITHOUT_GROUPS, 0));
            assertEquals("Total del rango", text(suppliers, FIRST_ROW_WITHOUT_GROUPS + 3, 0));
            assertEquals(45.50, suppliers.getRow(FIRST_ROW_WITHOUT_GROUPS + 3).getCell(2).getNumericCellValue());
        }
    }

    @Test
    @DisplayName("Un rango inválido no genera archivo: el error del reporte llega igual")
    void invalidRange_propagatesTheReportError() {
        when(reportService.getTotals(AUGUST_31, JULY_1))
                .thenThrow(new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta."));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> exportService.exportTotals(AUGUST_31, JULY_1));
        assertEquals("La fecha desde no puede ser posterior a la fecha hasta.", error.getMessage());
    }

    private static SalesByDayReportResponse.Row salesRow(LocalDate date, String total, String cost, String profit,
                                                         String margin) {
        return new SalesByDayReportResponse.Row(date, 30, new BigDecimal("74.250"), money(total), money(cost),
                money(profit), new BigDecimal(margin), 1, new BigDecimal("1.000"), money("104"), money("0"), money("0"),
                money(total).add(money("104")), money(profit));
    }

    private static SalesBySupplierReportResponse.Row supplierRow(String month, String quantity, String total) {
        return new SalesBySupplierReportResponse.Row(month, new BigDecimal(quantity), money(total), money("144"),
                money("16"), new BigDecimal("10.00"), BigDecimal.ZERO, money("0"), money(total));
    }

    private static TransactionsByCategoryReportResponse.Month categoryMonth(
            String month, List<CategoryRow> incomes, List<CategoryRow> expenses, BigDecimal incomeTotal,
            BigDecimal expenseTotal, List<TransferRow> transfers, long consumptions, BigDecimal consumptionTotal) {
        return new TransactionsByCategoryReportResponse.Month(month, incomes, incomeTotal, expenses, expenseTotal,
                incomeTotal.add(expenseTotal), transfers, consumptions, consumptionTotal);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount).setScale(2);
    }

    private static Workbook open(ReportFile file) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(file.content()));
    }

    private static String text(Sheet sheet, int row, int column) {
        return sheet.getRow(row).getCell(column).getStringCellValue();
    }

    private static void assertNumber(Cell cell, double value, String format) {
        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertEquals(value, cell.getNumericCellValue(), 0.0000001);
        assertEquals(format, cell.getCellStyle().getDataFormatString());
    }

    private static boolean isBold(Workbook workbook, Cell cell) {
        return workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold();
    }
}
