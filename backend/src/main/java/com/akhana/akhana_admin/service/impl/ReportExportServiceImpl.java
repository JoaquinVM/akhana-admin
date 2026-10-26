package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ConsumptionsReportResponse;
import com.akhana.akhana_admin.dto.ReportFile;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesBySupplierReportResponse;
import com.akhana.akhana_admin.dto.TotalsReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse.CategoryRow;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse.TransferRow;
import com.akhana.akhana_admin.service.ReportExportService;
import com.akhana.akhana_admin.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReportExportServiceImpl implements ReportExportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] MONTH_NAMES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final String RANGE_TOTAL = "Total del rango";

    private final ReportService reportService;

    @Override
    public ReportFile exportSalesByDay(LocalDate from, LocalDate to) {
        SalesByDayReportResponse report = reportService.getSalesByDay(from, to);
        return write("sales-by-day", report.from(), report.to(), book -> {
            SheetWriter sheet = book.sheet("Ventas por día", title("Ventas por día", report.from(), report.to()), 12);
            sheet.groups(new Group("", 1), new Group("Ventas normales", 6), new Group("Ventas al costo", 3),
                    new Group("Total", 2));
            sheet.headers("Día", "Ventas", "Cantidad", "Total", "Costo", "Utilidad", "Margen", "Ventas", "Cantidad",
                    "Total", "Total", "Utilidad");
            for (SalesByDayReportResponse.Month month : report.months()) {
                month.days().forEach(day -> sheet.row(false, salesByDayCells(Value.date(day.date()), day)));
                sheet.row(true, salesByDayCells(Value.text("Total " + monthLabel(month.month())), month.totals()));
            }
            sheet.row(true, salesByDayCells(Value.text(RANGE_TOTAL), report.totals()));
        });
    }

    @Override
    public ReportFile exportSalesBySupplier(LocalDate from, LocalDate to) {
        SalesBySupplierReportResponse report = reportService.getSalesBySupplier(from, to);
        return write("sales-by-supplier", report.from(), report.to(), book -> {
            SheetWriter sheet = book.sheet("Ventas por proveedor",
                    title("Ventas por proveedor y mes", report.from(), report.to()), 9);
            sheet.groups(new Group("", 1), new Group("Ventas normales", 5), new Group("Ventas al costo", 2),
                    new Group("", 1));
            sheet.headers("Proveedor / mes", "Cantidad", "Total", "Costo", "Utilidad", "Margen", "Cantidad", "Total",
                    "Total");
            for (SalesBySupplierReportResponse.Supplier supplier : report.suppliers()) {
                sheet.row(true, Value.text(supplier.supplierName()));
                supplier.months().forEach(month ->
                        sheet.row(false, salesBySupplierCells(Value.text("  " + monthLabel(month.month())), month)));
                sheet.row(true, salesBySupplierCells(Value.text("Total " + supplier.supplierName()), supplier.totals()));
            }
            sheet.row(true, salesBySupplierCells(Value.text(RANGE_TOTAL), report.totals()));
        });
    }

    @Override
    public ReportFile exportTransactionsByCategory(LocalDate from, LocalDate to) {
        TransactionsByCategoryReportResponse report = reportService.getTransactionsByCategory(from, to);
        // One column per month and the last one with the range total, like the pivot table and the screen
        List<TransactionsByCategoryReportResponse.Month> columns = new ArrayList<>(report.months());
        columns.add(report.totals());
        return write("transactions-by-category", report.from(), report.to(), book -> {
            SheetWriter sheet = book.sheet("Movimientos por categoría",
                    title("Movimientos por categoría y mes", report.from(), report.to()), columns.size() + 1);
            List<String> headers = new ArrayList<>(List.of("Categoría"));
            report.months().forEach(month -> headers.add(monthLabel(month.month())));
            headers.add(RANGE_TOTAL);
            sheet.headers(headers.toArray(String[]::new));

            sheet.row(true, Value.text("Ingresos"));
            lines(sheet, columns, report.totals().incomes(), TransactionsByCategoryReportResponse.Month::incomes,
                    (a, b) -> Objects.equals(a.categoryId(), b.categoryId()), CategoryRow::categoryName,
                    CategoryRow::amount);
            sheet.row(true, amounts("Total ingresos", columns, TransactionsByCategoryReportResponse.Month::incomeTotal));
            sheet.row(true, Value.text("Gastos"));
            lines(sheet, columns, report.totals().expenses(), TransactionsByCategoryReportResponse.Month::expenses,
                    (a, b) -> Objects.equals(a.categoryId(), b.categoryId()), CategoryRow::categoryName,
                    CategoryRow::amount);
            sheet.row(true, amounts("Total gastos", columns, TransactionsByCategoryReportResponse.Month::expenseTotal));
            sheet.row(true, amounts("Neto", columns, TransactionsByCategoryReportResponse.Month::net));

            if (!report.totals().transfers().isEmpty()) {
                sheet.row(true, Value.text("Transferencias (entre cuentas, no suman en el neto)"));
                lines(sheet, columns, report.totals().transfers(), TransactionsByCategoryReportResponse.Month::transfers,
                        (a, b) -> Objects.equals(a.sourceAccountId(), b.sourceAccountId())
                                && Objects.equals(a.destinationAccountId(), b.destinationAccountId()),
                        t -> t.sourceAccountName() + " → " + t.destinationAccountName(), TransferRow::amount);
            }
            if (report.totals().consumptions() > 0) {
                List<Value> cells = new ArrayList<>(List.of(Value.text("Consumo al costo (sin movimiento de dinero)")));
                columns.forEach(column -> cells.add(column.consumptions() > 0
                        ? Value.money(column.consumptionTotal().negate()) : Value.blank()));
                sheet.row(false, cells.toArray(Value[]::new));
            }
        });
    }

    @Override
    public ReportFile exportTotals(LocalDate from, LocalDate to) {
        TotalsReportResponse report = reportService.getTotals(from, to);
        return write("totals", report.from(), report.to(), book -> {
            SheetWriter sheet = book.sheet("Totales", title("Totales de ventas y utilidad", report.from(), report.to()),
                    12);
            sheet.groups(new Group("", 1), new Group("Ventas normales", 5), new Group("Ventas al costo", 2),
                    new Group("", 1), new Group("Consumos al costo", 2), new Group("", 1));
            sheet.headers("Mes", "Ventas", "Total", "Costo", "Utilidad", "Margen", "Ventas", "Total", "Total ventas",
                    "Consumos", "Total", "Utilidad menos consumos");
            report.months().forEach(month -> sheet.row(false, totalsCells(Value.text(monthLabel(month.month())), month)));
            sheet.row(true, totalsCells(Value.text(RANGE_TOTAL), report.totals()));
        });
    }

    @Override
    public ReportFile exportConsumptions(LocalDate from, LocalDate to) {
        ConsumptionsReportResponse report = reportService.getConsumptions(from, to);
        return write("consumptions", report.from(), report.to(), book -> {
            SheetWriter days = book.sheet("Por día", title("Consumos al costo por día", report.from(), report.to()), 4);
            days.headers("Día", "Consumos", "Productos", "Total al costo");
            for (ConsumptionsReportResponse.Month month : report.months()) {
                month.days().forEach(day -> days.row(false, consumptionDayCells(Value.date(day.date()), day)));
                days.row(true, consumptionDayCells(Value.text("Total " + monthLabel(month.month())), month.totals()));
            }
            days.row(true, consumptionDayCells(Value.text(RANGE_TOTAL), report.totals()));

            SheetWriter suppliers = book.sheet("Por proveedor y mes",
                    title("Consumos al costo por proveedor y mes", report.from(), report.to()), 3);
            suppliers.headers("Proveedor / mes", "Productos", "Total al costo");
            for (ConsumptionsReportResponse.Supplier supplier : report.suppliers()) {
                suppliers.row(true, Value.text(supplier.supplierName()));
                supplier.months().forEach(month -> suppliers.row(false,
                        consumptionSupplierCells(Value.text("  " + monthLabel(month.month())), month)));
                suppliers.row(true,
                        consumptionSupplierCells(Value.text("Total " + supplier.supplierName()), supplier.totals()));
            }
            suppliers.row(true, consumptionSupplierCells(Value.text(RANGE_TOTAL), report.supplierTotals()));
        });
    }

    private static Value[] salesByDayCells(Value label, SalesByDayReportResponse.Row row) {
        return new Value[]{label, Value.count(row.normalSales()), Value.quantity(row.normalQuantity()),
                Value.money(row.normalTotal()), Value.money(row.normalCost()), Value.money(row.normalProfit()),
                Value.percent(row.normalMargin()), Value.count(row.atCostSales()), Value.quantity(row.atCostQuantity()),
                Value.money(row.atCostTotal()), Value.money(row.total()), Value.money(row.profit())};
    }

    private static Value[] salesBySupplierCells(Value label, SalesBySupplierReportResponse.Row row) {
        return new Value[]{label, Value.quantity(row.normalQuantity()), Value.money(row.normalTotal()),
                Value.money(row.normalCost()), Value.money(row.normalProfit()), Value.percent(row.normalMargin()),
                Value.quantity(row.atCostQuantity()), Value.money(row.atCostTotal()), Value.money(row.total())};
    }

    private static Value[] totalsCells(Value label, TotalsReportResponse.Row row) {
        return new Value[]{label, Value.count(row.normalSales()), Value.money(row.normalTotal()),
                Value.money(row.normalCost()), Value.money(row.normalProfit()), Value.percent(row.normalMargin()),
                Value.count(row.atCostSales()), Value.money(row.atCostTotal()), Value.money(row.total()),
                Value.count(row.consumptions()), Value.money(row.consumptionTotal()),
                Value.money(row.profitAfterConsumptions())};
    }

    private static Value[] consumptionDayCells(Value label, ConsumptionsReportResponse.DayRow row) {
        return new Value[]{label, Value.count(row.consumptions()), Value.quantity(row.quantity()),
                Value.money(row.total())};
    }

    private static Value[] consumptionSupplierCells(Value label, ConsumptionsReportResponse.SupplierRow row) {
        return new Value[]{label, Value.quantity(row.quantity()), Value.money(row.total())};
    }

    // Rows come from the range total, which already has every category or transfer in order; a month without
    // movements of that line leaves its cell empty, like the screen's "—"
    private static <T> void lines(SheetWriter sheet, List<TransactionsByCategoryReportResponse.Month> columns,
                                  List<T> rows, Function<TransactionsByCategoryReportResponse.Month, List<T>> of,
                                  BiPredicate<T, T> sameLine, Function<T, String> label,
                                  Function<T, BigDecimal> amount) {
        for (T row : rows) {
            List<Value> cells = new ArrayList<>(List.of(Value.text("  " + label.apply(row))));
            columns.forEach(column -> cells.add(of.apply(column).stream()
                    .filter(candidate -> sameLine.test(candidate, row))
                    .findFirst()
                    .map(found -> Value.money(amount.apply(found)))
                    .orElse(Value.blank())));
            sheet.row(false, cells.toArray(Value[]::new));
        }
    }

    private static Value[] amounts(String label, List<TransactionsByCategoryReportResponse.Month> columns,
                                   Function<TransactionsByCategoryReportResponse.Month, BigDecimal> amount) {
        List<Value> cells = new ArrayList<>(List.of(Value.text(label)));
        columns.forEach(column -> cells.add(Value.money(amount.apply(column))));
        return cells.toArray(Value[]::new);
    }

    private static String title(String name, LocalDate from, LocalDate to) {
        return "%s del %s al %s".formatted(name, from.format(DAY), to.format(DAY));
    }

    /** "2026-07" → "julio 2026", like the screen. */
    static String monthLabel(String month) {
        String[] parts = month.split("-");
        return MONTH_NAMES[Integer.parseInt(parts[1]) - 1] + " " + parts[0];
    }

    private static ReportFile write(String report, LocalDate from, LocalDate to,
                                    Consumer<Book> content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            content.accept(new Book(workbook));
            workbook.write(out);
            return new ReportFile("akhana-%s-%s-%s.xlsx".formatted(report, from, to), out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo del reporte", e);
        }
    }

    private enum Kind { TEXT, DATE, COUNT, QUANTITY, MONEY, PERCENT }

    /** A cell: amounts are real numbers with 2 decimals, quantities with 3, the margin a percentage. */
    private record Value(Kind kind, Object value) {
        static Value text(String text) {
            return new Value(Kind.TEXT, text);
        }

        static Value date(LocalDate date) {
            return new Value(Kind.DATE, date);
        }

        static Value count(long count) {
            return new Value(Kind.COUNT, count);
        }

        static Value quantity(BigDecimal quantity) {
            return new Value(Kind.QUANTITY, quantity == null ? null : quantity.setScale(3, RoundingMode.HALF_UP));
        }

        static Value money(BigDecimal amount) {
            return new Value(Kind.MONEY, amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP));
        }

        // The report's margin is 23.35 (%); Excel keeps 0.2335 shown as 23.35 %
        static Value percent(BigDecimal margin) {
            return new Value(Kind.PERCENT, margin == null ? null : margin.movePointLeft(2));
        }

        static Value blank() {
            return new Value(Kind.TEXT, null);
        }
    }

    private record Group(String label, int span) {
    }

    /** The workbook and its shared cell styles (Excel limits how many a file can have). */
    private static final class Book {
        private final Workbook workbook;
        private final Map<String, CellStyle> styles = new HashMap<>();

        Book(Workbook workbook) {
            this.workbook = workbook;
        }

        SheetWriter sheet(String name, String title, int columns) {
            return new SheetWriter(this, workbook.createSheet(name), title, columns);
        }

        CellStyle style(Kind kind, boolean bold, boolean header) {
            return styles.computeIfAbsent(kind + "|" + bold + "|" + header, key -> {
                CellStyle style = workbook.createCellStyle();
                String format = switch (kind) {
                    case DATE -> "dd/mm/yyyy";
                    case COUNT -> "#,##0";
                    case QUANTITY -> "#,##0.000";
                    case MONEY -> "#,##0.00";
                    case PERCENT -> "0.00%";
                    case TEXT -> null;
                };
                if (format != null) {
                    style.setDataFormat(workbook.createDataFormat().getFormat(format));
                }
                if (bold || header) {
                    Font font = workbook.createFont();
                    font.setBold(true);
                    style.setFont(font);
                }
                if (header) {
                    style.setAlignment(HorizontalAlignment.CENTER);
                    style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    style.setBorderBottom(BorderStyle.THIN);
                }
                return style;
            });
        }
    }

    /** Writes a title, the headers and the rows of one sheet, top to bottom. */
    private static final class SheetWriter {
        private final Book book;
        private final Sheet sheet;
        private int rowIndex;

        SheetWriter(Book book, Sheet sheet, String title, int columns) {
            this.book = book;
            this.sheet = sheet;
            Cell titleCell = sheet.createRow(rowIndex++).createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(book.style(Kind.TEXT, true, false));
            rowIndex++;
            sheet.setColumnWidth(0, 36 * 256);
            for (int column = 1; column < columns; column++) {
                sheet.setColumnWidth(column, 16 * 256);
            }
        }

        void groups(Group... groups) {
            Row row = sheet.createRow(rowIndex);
            int column = 0;
            for (Group group : groups) {
                for (int i = 0; i < group.span(); i++) {
                    Cell cell = row.createCell(column + i);
                    cell.setCellStyle(book.style(Kind.TEXT, false, true));
                    if (i == 0) {
                        cell.setCellValue(group.label());
                    }
                }
                if (group.span() > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, column, column + group.span() - 1));
                }
                column += group.span();
            }
            rowIndex++;
        }

        void headers(String... names) {
            Row row = sheet.createRow(rowIndex++);
            for (int column = 0; column < names.length; column++) {
                Cell cell = row.createCell(column);
                cell.setCellValue(names[column]);
                cell.setCellStyle(book.style(Kind.TEXT, false, true));
            }
            sheet.createFreezePane(1, rowIndex);
        }

        void row(boolean bold, Value... values) {
            Row row = sheet.createRow(rowIndex++);
            for (int column = 0; column < values.length; column++) {
                Value value = values[column];
                Cell cell = row.createCell(column);
                cell.setCellStyle(book.style(value.kind(), bold, false));
                switch (value.value()) {
                    case null -> cell.setBlank();
                    case String text -> cell.setCellValue(text);
                    case LocalDate date -> cell.setCellValue(date);
                    case Long count -> cell.setCellValue(count);
                    case BigDecimal number -> cell.setCellValue(number.doubleValue());
                    default -> throw new IllegalStateException("Unexpected cell value " + value.value());
                }
            }
        }
    }
}
