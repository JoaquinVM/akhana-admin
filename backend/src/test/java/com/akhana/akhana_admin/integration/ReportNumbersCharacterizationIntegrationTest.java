package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.DashboardResponse;
import com.akhana.akhana_admin.dto.ReportFile;
import com.akhana.akhana_admin.service.DashboardService;
import com.akhana.akhana_admin.service.ReportExportService;
import com.akhana.akhana_admin.service.ReportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Characterization of the money the reports, their export and the dashboard show (FIN-10, CON-03, DSH-01 to
 * DSH-04): three days of sales with line and general discounts, a voided sale, third-party, pending,
 * unidentified and assigned lines, an at-cost sale, a presentation by weight and consumptions. The expected text
 * was taken before refactoring the queries and the discount spreading, so any change of a single cent fails here.
 * The dashboard's "now" block (cash, accounts, debts) reads the whole base and is left out.
 */
@SpringBootTest
@Transactional
class ReportNumbersCharacterizationIntegrationTest {

    private static final String SNAPSHOT = "/characterization/report-numbers.txt";
    private static final LocalDate FROM = LocalDate.of(2003, 3, 10);
    private static final LocalDate TO = LocalDate.of(2003, 3, 12);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportExportService reportExportService;

    @Autowired
    private DashboardService dashboardService;

    private final Map<UUID, String> labels = new HashMap<>();
    private final Map<String, UUID> sessions = new HashMap<>();

    @Test
    void reportsExportAndDashboardShowTheSameNumbersAsBefore() throws IOException {
        seed();

        StringBuilder out = new StringBuilder();
        section(out, "salesByDay", describe(reportService.getSalesByDay(FROM, TO)));
        section(out, "salesBySupplier", describe(reportService.getSalesBySupplier(FROM, TO)));
        section(out, "totals", describe(reportService.getTotals(FROM, TO)));
        section(out, "consumptions", describe(reportService.getConsumptions(FROM, TO)));
        section(out, "transactionsByCategory", describe(reportService.getTransactionsByCategory(FROM, TO)));
        section(out, "dashboard", describe(dashboardService.getDashboard(FROM, TO)));
        section(out, "exportSalesByDay", cells(reportExportService.exportSalesByDay(FROM, TO)));
        section(out, "exportSalesBySupplier", cells(reportExportService.exportSalesBySupplier(FROM, TO)));
        section(out, "exportTotals", cells(reportExportService.exportTotals(FROM, TO)));
        section(out, "exportConsumptions", cells(reportExportService.exportConsumptions(FROM, TO)));
        String actual = out.toString();

        String expected;
        try (InputStream in = getClass().getResourceAsStream(SNAPSHOT)) {
            if (in == null) {
                Files.writeString(actualFile(), actual);
            }
            assertNotNull(in, "Missing " + SNAPSHOT + "; the current output is in " + actualFile());
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!expected.equals(actual)) {
            Files.writeString(actualFile(), actual);
        }
        assertEquals(expected, actual);
    }

    /** Where the current output goes when it differs: build/, or REPORT_NUMBERS_OUT to compare it by hand. */
    private static Path actualFile() {
        String out = System.getenv("REPORT_NUMBERS_OUT");
        return out != null ? Path.of(out) : Path.of("build", "report-numbers-actual.txt");
    }

    private void seed() {
        UUID category = id("INSERT INTO categories (name, color, created_by) VALUES ('CHR categoría', '#000000', 'it-chr') RETURNING id");
        UUID supplierA = label(id("INSERT INTO suppliers (name, code, created_by) VALUES ('CHR Proveedor A', 'CHR-A', 'it-chr') RETURNING id"), "supplierA");
        UUID supplierB = label(id("INSERT INTO suppliers (name, code, created_by) VALUES ('CHR Proveedor B', 'CHR-B', 'it-chr') RETURNING id"), "supplierB");
        UUID p1 = product("P1", category, supplierA);
        UUID p2 = product("P2", category, supplierB);
        UUID p3 = product("P3", category, supplierA);
        UUID p4 = product("P4", category, supplierB);
        UUID presentation = id("INSERT INTO product_presentations (product_id, name, base_quantity, fixed_price, created_by) "
                + "VALUES ('" + p4 + "', '1/2 libra', 0.227, 45.00, 'it-chr') RETURNING id");
        UUID mami = id("INSERT INTO creditors (name, created_by) VALUES ('CHR Mami', 'it-chr') RETURNING id");
        UUID customer = id("INSERT INTO customers (name, created_by) VALUES ('CHR Cliente', 'it-chr') RETURNING id");

        // Day 10: a line discount and a general discount of 1.67 over 35 with Mami's 7 kept whole (P-44)
        UUID s1 = sale("S1", "2003-03-10", "EFECTIVO", "NORMAL", "42.00", "40.33", "40.33", "0", "0", null);
        item(s1, p1, "12.00", "2.00", "10.00", "2", "20.00", "6.00");
        free(s1, p1, "THIRD_PARTY", "7.00", "7.00", null, mami);
        item(s1, p2, "15.00", "0", "15.00", "1", "15.00", "9.00");
        UUID s2 = sale("S2", "2003-03-10", "QR", "NORMAL", "30.00", "30.00", "0", "30.00", "0", null);
        item(s2, p3, "10.00", "0", "10.00", "1", "10.00", "4.50");
        free(s2, p3, "UNIDENTIFIED", "20.00", "0", "POR_ASIGNAR", null);
        UUID s3 = sale("S3", "2003-03-10", "EFECTIVO", "NORMAL", "50.00", "50.00", "50.00", "0", "0", null);
        item(s3, p1, "10.00", "0", "10.00", "5", "50.00", "6.00");
        jdbc.update("UPDATE sales SET status = 'ANULADA', voided_by = 'it-chr', voided_at = now(), void_reason = 'CHR' WHERE id = ?", s3);
        UUID s4 = sale("S4", "2003-03-10", "CREDITO", "AL_COSTO", "18.00", "18.00", "0", "0", "18.00", customer);
        item(s4, p1, "6.00", "0", "6.00", "3", "18.00", "6.00");

        // Day 11: three equal lines with a discount of 10 (3.33 each and a cent left over), a presentation by
        // weight with a general discount, and a sale with only Mami's products
        UUID s5 = sale("S5", "2003-03-11", "MIXTO", "NORMAL", "30.00", "20.00", "10.00", "10.00", "0", null);
        item(s5, p1, "10.00", "0", "10.00", "1", "10.00", "6.00");
        item(s5, p2, "15.00", "5.00", "10.00", "1", "10.00", "9.00");
        item(s5, p3, "10.00", "0", "10.00", "1", "10.00", "4.50");
        UUID s6 = sale("S6", "2003-03-11", "EFECTIVO", "NORMAL", "90.00", "85.50", "85.50", "0", "0", null);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
                + "quantity, subtotal, presentation_id, presentation_name, presentation_base_quantity, base_quantity) "
                + "VALUES (?, ?, 'CHR P4', 45.00, 130.00, 45.00, 2, 90.00, ?, '1/2 libra', 0.227, 0.454)", s6, p4, presentation);
        UUID s7 = sale("S7", "2003-03-11", "EFECTIVO", "NORMAL", "15.00", "15.00", "15.00", "0", "0", null);
        free(s7, p1, "THIRD_PARTY", "15.00", "15.00", null, mami);

        // Day 12: an unidentified line closed for good, and an assigned one beside a line with an unrounded cost
        UUID s8 = sale("S8", "2003-03-12", "EFECTIVO", "NORMAL", "70.00", "70.00", "70.00", "0", "0", null);
        item(s8, p2, "50.00", "0", "50.00", "1", "50.00", "30.00");
        free(s8, p2, "UNIDENTIFIED", "20.00", "0", "SIN_IDENTIFICAR", null);
        UUID s9 = sale("S9", "2003-03-12", "QR", "NORMAL", "41.00", "39.99", "0", "39.99", "0", null);
        item(s9, p3, "7.00", "0", "7.00", "3", "21.00", "4.333");
        free(s9, p3, "UNIDENTIFIED", "20.00", "12.00", "ASIGNADO", null);

        // The dashboard compares with the same days of the week before (2003-03-10 is a Monday)
        UUID s10 = sale("S10", "2003-03-04", "EFECTIVO", "NORMAL", "10.00", "10.00", "10.00", "0", "0", null);
        item(s10, p1, "10.00", "0", "10.00", "1", "10.00", "6.00");

        UUID c1 = id("INSERT INTO consumptions (consumption_number, consumption_date, total_amount, created_by) "
                + "VALUES ('CHR-C1', '2003-03-12', 35.51, 'it-chr') RETURNING id");
        jdbc.update("INSERT INTO consumption_items (consumption_id, product_id, product_name, quantity, unit_cost, subtotal) "
                + "VALUES (?, ?, 'CHR P1', 1, 6.00, 6.00)", c1, p1);
        jdbc.update("INSERT INTO consumption_items (consumption_id, product_id, product_name, quantity, unit_cost, subtotal, "
                + "presentation_id, presentation_name, presentation_base_quantity, base_quantity) "
                + "VALUES (?, ?, 'CHR P4', 1, 130.00, 29.51, ?, '1/2 libra', 0.227, 0.227)", c1, p4, presentation);
        UUID c2 = id("INSERT INTO consumptions (consumption_number, consumption_date, total_amount, status, created_by, "
                + "voided_by, voided_at, void_reason) VALUES ('CHR-C2', '2003-03-11', 9.00, 'ANULADO', 'it-chr', 'it-chr', now(), 'CHR') RETURNING id");
        jdbc.update("INSERT INTO consumption_items (consumption_id, product_id, product_name, quantity, unit_cost, subtotal) "
                + "VALUES (?, ?, 'CHR P2', 1, 9.00, 9.00)", c2, p2);

        UUID account = label(id("INSERT INTO financial_accounts (name, color, created_by) VALUES ('CHR Cuenta', '#123456', 'it-chr') RETURNING id"), "account");
        UUID expense = label(id("INSERT INTO financial_categories (name, type, color, created_by) VALUES ('CHR Gasto', 'GASTO', '#654321', 'it-chr') RETURNING id"), "expenseCategory");
        jdbc.update("INSERT INTO financial_transactions (type, source_account_id, category_id, amount, transaction_date, "
                + "operation_date, description, created_by) VALUES ('GASTO_MANUAL', ?, ?, 12.50, '2003-03-11T15:00:00Z', "
                + "'2003-03-11', 'CHR gasto', 'it-chr')", account, expense);
    }

    private UUID product(String name, UUID category, UUID supplier) {
        return label(jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, created_by) "
                        + "VALUES (?, ?, ?, ?, 6.00, 10.00, 4.00, 66.67, 'it-chr') RETURNING id",
                UUID.class, "CHR-" + name, "CHR " + name, category, supplier), name);
    }

    private UUID sale(String number, String day, String method, String type, String subtotal, String total,
                      String cash, String qr, String credit, UUID customer) {
        UUID session = sessions.computeIfAbsent(day, d -> jdbc.queryForObject("INSERT INTO cash_sessions (status, "
                + "opening_amount, opened_by, operation_date) VALUES ('CERRADA', 0, 'it-chr', ?::date) RETURNING id",
                UUID.class, d));
        return label(jdbc.queryForObject(
                "INSERT INTO sales (sale_number, cash_session_id, payment_method, sale_type, subtotal_amount, total_amount, amount_cash, "
                        + "amount_qr, credit_amount, customer_id, created_by, created_at, operation_date) "
                        + "VALUES (?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?, 'it-chr', "
                        + "?::date + time '15:00', ?::date) RETURNING id",
                UUID.class, "CHR-" + number, session, method, type, subtotal, total, cash, qr, credit, customer, day, day), number);
    }

    private void item(UUID sale, UUID product, String price, String discount, String finalPrice, String quantity,
                      String subtotal, String cost) {
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, discount_per_unit, "
                        + "final_unit_price, quantity, subtotal, unit_cost) "
                        + "VALUES (?, ?, 'CHR item', ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::numeric)",
                sale, product, price, discount, finalPrice, quantity, subtotal, cost);
    }

    private void free(UUID sale, UUID product, String type, String price, String cost, String status, UUID creditor) {
        boolean closed = "SIN_IDENTIFICAR".equals(status) || "ASIGNADO".equals(status);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
                        + "quantity, subtotal, free_line_type, free_name, assignment_status, creditor_id, assigned_by, assigned_at) "
                        + "VALUES (?, ?, 'CHR libre', ?::numeric, ?::numeric, ?::numeric, 1, ?::numeric, ?, 'CHR libre', ?, ?, ?, ?)",
                sale, product, price, cost, price, price, type, status, creditor,
                closed ? "it-chr" : null, closed ? java.sql.Timestamp.from(java.time.Instant.parse("2003-03-13T15:00:00Z")) : null);
    }

    private UUID id(String sql) {
        return jdbc.queryForObject(sql, UUID.class);
    }

    private UUID label(UUID id, String name) {
        labels.put(id, name);
        return id;
    }

    private static void section(StringBuilder out, String name, String body) {
        out.append("== ").append(name).append('\n').append(body).append('\n');
    }

    /** Records field by field, with each known id replaced by its name; the dashboard's "now" is left out. */
    private String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof UUID id) {
            return labels.getOrDefault(id, "other-id");
        }
        if (value instanceof Collection<?> items) {
            List<String> parts = new ArrayList<>();
            items.forEach(item -> parts.add(describe(item)));
            return "[" + String.join(",\n ", parts) + "]";
        }
        if (value.getClass().isRecord()) {
            List<String> parts = new ArrayList<>();
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                if (value instanceof DashboardResponse && component.getName().equals("now")) {
                    continue;
                }
                try {
                    parts.add(component.getName() + "=" + describe(component.getAccessor().invoke(value)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            return value.getClass().getSimpleName() + "{" + String.join(", ", parts) + "}";
        }
        return value.toString();
    }

    /** Every cell of every sheet: the value or the formula, so the file's numbers and totals are compared. */
    private static String cells(ReportFile file) throws IOException {
        StringBuilder out = new StringBuilder(file.fileName()).append('\n');
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            for (Sheet sheet : workbook) {
                out.append("# ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    for (Cell cell : row) {
                        values.add(cellText.apply(cell));
                    }
                    out.append(row.getRowNum()).append(": ").append(String.join(" | ", values)).append('\n');
                }
            }
        }
        return out.toString();
    }

    private static final Function<Cell, String> cellText = cell -> switch (cell.getCellType()) {
        case NUMERIC -> String.valueOf(cell.getNumericCellValue());
        case STRING -> cell.getStringCellValue();
        case FORMULA -> "=" + cell.getCellFormula();
        case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
        default -> "";
    };
}
