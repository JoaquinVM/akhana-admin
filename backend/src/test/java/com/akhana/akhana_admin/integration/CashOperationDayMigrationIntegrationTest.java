package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.repository.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAJ-11 on deploy: the backfill of V21 (each record keeps the store day it is counted on today) and the one-time
 * correction of V22 (sessions #146, #147 and #148 to 28, 29 and 30-10-2026). Flyway already ran both on this database,
 * so each test seeds its own rows and runs the SQL of the migration again inside the test transaction, which is
 * rolled back at the end: nothing it creates or changes stays. Sessions #146-#148 that already exist are renumbered
 * first (also rolled back) so only the seeded ones match.
 */
@SpringBootTest
@Transactional
class CashOperationDayMigrationIntegrationTest {

    private static final String FIX = "db/migration/V22__cash_sessions_146_148_day_fix.sql";
    private static final String BACKFILL = "db/migration/V21__cash_operation_day.sql";
    private static final LocalDate OCT_28 = LocalDate.of(2026, 10, 28);
    private static final LocalDate OCT_29 = LocalDate.of(2026, 10, 29);
    private static final LocalDate OCT_30 = LocalDate.of(2026, 10, 30);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);
    private static final String[] TABLES = {"cash_sessions", "sales", "financial_transactions", "purchase_payments",
        "creditor_repayments", "customer_collections"};

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private SaleRepository saleRepository;

    private String tag;
    private UUID caja;
    private UUID bisa;
    private UUID purchase;
    private UUID creditor;
    private UUID customer;

    @BeforeEach
    void setUp() {
        tag = "it-day-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("UPDATE cash_sessions SET session_number = session_number + 1000000000 "
            + "WHERE session_number IN (146, 147, 148)");
        // Only one open session is allowed; a real one is closed for the test (rolled back)
        jdbc.update("UPDATE cash_sessions SET status = 'CERRADA' WHERE status = 'ABIERTA'");
        caja = account("Caja IT");
        bisa = account("Bisa IT");
        UUID supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it') RETURNING id",
            UUID.class, "IT proveedor " + tag, "IT-" + tag);
        purchase = jdbc.queryForObject("INSERT INTO purchases (purchase_number, supplier_id, purchase_date, items_total, "
            + "total_amount, paid_amount, created_by) VALUES (?, ?, '2026-10-20', 500, 500, 30, 'it') RETURNING id",
            UUID.class, "PED-" + tag, supplier);
        creditor = jdbc.queryForObject("INSERT INTO creditors (name, created_by) VALUES (?, 'it') RETURNING id",
            UUID.class, "IT Mami " + tag);
        customer = jdbc.queryForObject("INSERT INTO customers (name, created_by) VALUES (?, 'it') RETURNING id",
            UUID.class, "IT Neida " + tag);
    }

    @Test
    @DisplayName("CAJ-11: la #146, #147 y #148 cerradas pasan al 28, 29 y 30/10 con sus ventas (también anuladas), "
        + "movimientos, ingresos QR, pagos, devoluciones y cobros; nada más cambia")
    void movesTheClosedSessionsOfTheWindowWithEverythingOfTheirs() throws IOException {
        // #146 opened 29-10 09:10 and closed 30-10 08:00 (La Paz); #147 opened 30-10; #148 opened 31-10 to load 30-10
        UUID s146 = session(146, "CERRADA", at("2026-10-29T09:10"), false);
        UUID s147 = session(147, "CERRADA", at("2026-10-30T10:00"), false);
        UUID s148 = session(148, "CERRADA", at("2026-10-31T09:00"), false);
        UUID other = session(1_000_145, "CERRADA", at("2026-10-29T08:00"), false);

        UUID sale = sale(s146, "100.00", at("2026-10-29T15:00"), "COMPLETADA");
        UUID afterMidnight = sale(s146, "25.00", at("2026-10-30T00:20"), "COMPLETADA");
        UUID voidedSale = sale(s146, "60.00", at("2026-10-29T16:00"), "ANULADA");
        UUID cashIncome = movement("VENTA_INGRESO", caja, null, sale, null, "ACTIVO", at("2026-10-29T15:00"));
        UUID qrIncome = movement("VENTA_INGRESO", bisa, null, afterMidnight, null, "ACTIVO", at("2026-10-30T00:20"));
        UUID voidedIncome = movement("VENTA_INGRESO", caja, null, voidedSale, null, "ANULADO", at("2026-10-29T16:00"));
        UUID expense = movement("GASTO_MANUAL", caja, s146, null, null, "ACTIVO", at("2026-10-29T18:00"));
        UUID voidedExpense = movement("GASTO_MANUAL", caja, s146, null, null, "ANULADO", at("2026-10-29T18:30"));
        UUID adjustment = movement("GASTO_MANUAL", caja, s146, null, null, "ACTIVO", at("2026-10-30T08:00"));
        UUID paymentTx = movement("GASTO_MANUAL", caja, s146, null, null, "ACTIVO", at("2026-10-29T19:00"));
        UUID payment = jdbc.queryForObject("INSERT INTO purchase_payments (purchase_id, account_id, amount, payment_date, "
            + "operation_date, financial_transaction_id, created_by) VALUES (?, ?, 30, ?, ?, ?, 'it') RETURNING id",
            UUID.class, purchase, caja, ts(at("2026-10-29T19:00")), OCT_29, paymentTx);
        UUID repayment = jdbc.queryForObject("INSERT INTO creditor_repayments (creditor_id, account_id, amount, "
            + "repayment_date, operation_date, created_by) VALUES (?, ?, 15, ?, ?, 'it') RETURNING id",
            UUID.class, creditor, caja, ts(at("2026-10-30T00:30")), OCT_30);
        UUID repaymentTx = movement("GASTO_MANUAL", caja, s146, null, null, "ACTIVO", at("2026-10-30T00:30"));
        jdbc.update("UPDATE financial_transactions SET creditor_repayment_id = ? WHERE id = ?", repayment, repaymentTx);
        UUID collection = jdbc.queryForObject("INSERT INTO customer_collections (customer_id, account_id, amount, "
            + "collection_date, operation_date, created_by) VALUES (?, ?, 20, ?, ?, 'it') RETURNING id",
            UUID.class, customer, caja, ts(at("2026-10-29T20:00")), OCT_29);
        UUID collectionTx = movement("INGRESO_MANUAL", caja, s146, null, null, "ACTIVO", at("2026-10-29T20:00"));
        jdbc.update("UPDATE financial_transactions SET customer_collection_id = ? WHERE id = ?", collection, collectionTx);

        UUID sale147 = sale(s147, "70.00", at("2026-10-30T11:00"), "COMPLETADA");
        UUID expense147 = movement("GASTO_MANUAL", caja, s147, null, null, "ACTIVO", at("2026-10-30T12:00"));
        UUID sale148 = sale(s148, "40.00", at("2026-10-31T10:00"), "COMPLETADA");
        // Not of those sessions: another session, and a movement of Bisa without session or sale
        UUID otherSale = sale(other, "9.00", at("2026-10-29T09:00"), "COMPLETADA");
        UUID bisaExpense = movement("GASTO_MANUAL", bisa, null, null, null, "ACTIVO", at("2026-10-29T09:30"));
        UUID personPayment = jdbc.queryForObject("INSERT INTO purchase_payments (purchase_id, creditor_id, amount, "
            + "payment_date, operation_date, created_by) VALUES (?, ?, 5, ?, ?, 'it') RETURNING id",
            UUID.class, purchase, creditor, ts(at("2026-10-29T10:00")), OCT_29);

        Map<String, Object> rowsBefore = snapshot();
        Map<LocalDate, BigDecimal> salesBefore = salesByDay();

        runScript(FIX);

        assertThat(day("cash_sessions", s146)).isEqualTo(OCT_28);
        assertThat(day("cash_sessions", s147)).isEqualTo(OCT_29);
        assertThat(day("cash_sessions", s148)).isEqualTo(OCT_30);
        for (UUID id : List.of(sale, afterMidnight, voidedSale)) {
            assertThat(day("sales", id)).isEqualTo(OCT_28);
        }
        for (UUID id : List.of(cashIncome, qrIncome, voidedIncome, expense, voidedExpense, adjustment, paymentTx,
                repaymentTx, collectionTx)) {
            assertThat(day("financial_transactions", id)).as("movement %s", id).isEqualTo(OCT_28);
        }
        assertThat(day("purchase_payments", payment)).isEqualTo(OCT_28);
        assertThat(day("creditor_repayments", repayment)).isEqualTo(OCT_28);
        assertThat(day("customer_collections", collection)).isEqualTo(OCT_28);
        assertThat(day("sales", sale147)).isEqualTo(OCT_29);
        assertThat(day("financial_transactions", expense147)).isEqualTo(OCT_29);
        assertThat(day("sales", sale148)).isEqualTo(OCT_30);
        // Untouched
        assertThat(day("cash_sessions", other)).isEqualTo(OCT_29);
        assertThat(day("sales", otherSale)).isEqualTo(OCT_29);
        assertThat(day("financial_transactions", bisaExpense)).isEqualTo(OCT_29);
        assertThat(day("purchase_payments", personPayment)).isEqualTo(OCT_29);

        // Only the day of operation changes: amounts, closing, real dates, numbers, users and balances stay
        assertThat(snapshot()).isEqualTo(rowsBefore);

        // Sales by day: 28-10 now has the sales of #146, 29-10 those of #147 and 30-10 those of #148
        Map<LocalDate, BigDecimal> salesAfter = salesByDay();
        assertThat(delta(salesBefore, salesAfter, OCT_28)).isEqualByComparingTo("125.00");
        assertThat(delta(salesBefore, salesAfter, OCT_29)).isEqualByComparingTo("-30.00");
        assertThat(delta(salesBefore, salesAfter, OCT_30)).isEqualByComparingTo("-55.00");
        assertThat(delta(salesBefore, salesAfter, OCT_31)).isEqualByComparingTo("-40.00");
    }

    @Test
    @DisplayName("CAJ-11: la corrección no toca una caja abierta, histórica o abierta fuera del 28/10 al 2/11")
    void leavesSessionsThatAreOpenHistoricalOrOutsideTheWindow() throws IOException {
        UUID open = session(146, "ABIERTA", at("2026-10-29T09:10"), false);
        UUID historical = session(147, "CERRADA", at("2026-10-30T09:00"), true);
        // 23:30 of 27-10 in La Paz is already 28-10 in UTC: still outside the window
        UUID before = session(148, "CERRADA", at("2026-10-27T23:30"), false);
        UUID openSale = sale(open, "10.00", at("2026-10-29T10:00"), "COMPLETADA");
        UUID openExpense = movement("GASTO_MANUAL", caja, open, null, null, "ACTIVO", at("2026-10-29T11:00"));
        UUID beforeSale = sale(before, "10.00", at("2026-10-27T23:40"), "COMPLETADA");

        runScript(FIX);

        assertThat(day("cash_sessions", open)).isEqualTo(OCT_29);
        assertThat(day("sales", openSale)).isEqualTo(OCT_29);
        assertThat(day("financial_transactions", openExpense)).isEqualTo(OCT_29);
        assertThat(day("cash_sessions", historical)).isEqualTo(OCT_30);
        assertThat(day("cash_sessions", before)).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(day("sales", beforeSale)).isEqualTo(LocalDate.of(2026, 10, 27));

        // After the window (opened 03-11) it is left as well
        jdbc.update("UPDATE cash_sessions SET opened_at = ?, operation_date = '2026-11-03' WHERE id = ?",
            ts(at("2026-11-03T09:00")), before);
        runScript(FIX);
        assertThat(day("cash_sessions", before)).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("CAJ-11: en una base sin esas cajas la corrección corre sin cambiar nada")
    void runsWithoutTheSessions() throws IOException {
        UUID other = session(1_000_146, "CERRADA", at("2026-10-29T09:10"), false);
        UUID otherSale = sale(other, "10.00", at("2026-10-30T00:20"), "COMPLETADA");
        Map<String, Object> before = snapshot();

        runScript(FIX);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE session_number IN (146, 147, 148)",
            Integer.class)).isZero();
        assertThat(day("sales", otherSale)).isEqualTo(OCT_30);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("CAJ-11 al desplegar: cada caja toma el día de su apertura y cada venta, movimiento, pago, devolución "
        + "y cobro el día en que se registró (La Paz); las históricas, el de la planilla")
    void backfillKeepsTheStoreDayOfEachRecord() throws IOException {
        // Opened 31-10 09:10 in La Paz; a sale of it at 00:20 of 01-11 keeps 01-11, as the reports counted it
        UUID session = session(1_000_147, "CERRADA", at("2026-10-31T09:10"), false);
        UUID lateSale = sale(session, "10.00", at("2026-11-01T00:20"), "COMPLETADA");
        UUID lastMinute = movement("GASTO_MANUAL", caja, session, null, null, "ACTIVO", at("2026-10-31T23:59"));
        UUID historicalImport = jdbc.queryForObject("INSERT INTO historical_imports (cutoff_date, imported_by) "
            + "VALUES ('2026-09-30', ?) RETURNING id", UUID.class, tag);
        UUID historicalSale = jdbc.queryForObject("INSERT INTO sales (sale_number, payment_method, total_amount, "
            + "subtotal_amount, historical_import_id, created_by, created_at, operation_date) "
            + "VALUES (?, 'HISTORICO', 50, 50, ?, ?, ?, '2000-01-01') RETURNING id",
            UUID.class, "HIS-" + tag, historicalImport, tag, ts(at("2026-09-15T12:00")));
        UUID payment = jdbc.queryForObject("INSERT INTO purchase_payments (purchase_id, creditor_id, amount, "
            + "payment_date, operation_date, created_by) VALUES (?, ?, 5, ?, '2000-01-01', 'it') RETURNING id",
            UUID.class, purchase, creditor, ts(at("2026-10-31T22:00")));
        UUID repayment = jdbc.queryForObject("INSERT INTO creditor_repayments (creditor_id, account_id, amount, "
            + "repayment_date, operation_date, created_by) VALUES (?, ?, 5, ?, '2000-01-01', 'it') RETURNING id",
            UUID.class, creditor, bisa, ts(at("2026-11-01T00:10")));
        UUID collection = jdbc.queryForObject("INSERT INTO customer_collections (customer_id, account_id, amount, "
            + "collection_date, operation_date, created_by) VALUES (?, ?, 5, ?, '2000-01-01', 'it') RETURNING id",
            UUID.class, customer, bisa, ts(at("2026-10-31T07:00")));
        // Seeded with a wrong day so the backfill has to set it
        for (String table : TABLES) {
            jdbc.update("UPDATE " + table + " SET operation_date = '2000-01-01' WHERE id IN (?, ?, ?, ?, ?, ?, ?)",
                session, lateSale, lastMinute, historicalSale, payment, repayment, collection);
        }

        // The data statements of V21 (its ALTER TABLE statements already ran)
        String script = StreamUtils.copyToString(new ClassPathResource(BACKFILL).getInputStream(), StandardCharsets.UTF_8);
        Arrays.stream(script.split(";"))
            .map(statement -> statement.lines().filter(line -> !line.startsWith("--")).reduce("", (a, b) -> a + "\n" + b).trim())
            .filter(statement -> statement.startsWith("UPDATE"))
            .forEach(jdbc::update);

        assertThat(day("cash_sessions", session)).isEqualTo(OCT_31);
        assertThat(day("sales", lateSale)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(day("financial_transactions", lastMinute)).isEqualTo(OCT_31);
        assertThat(day("sales", historicalSale)).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(day("purchase_payments", payment)).isEqualTo(OCT_31);
        assertThat(day("creditor_repayments", repayment)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(day("customer_collections", collection)).isEqualTo(OCT_31);
        // Nothing left that differs from the day it was recorded
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales "
            + "WHERE operation_date <> (created_at AT TIME ZONE 'America/La_Paz')::date", Integer.class)).isZero();
    }

    // --- helpers ---

    private void runScript(String path) {
        // The connection of the test transaction, so everything is rolled back
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(path));
    }

    /** Every column of every row of the six tables except operation_date. */
    private Map<String, Object> snapshot() {
        Map<String, Object> rows = new HashMap<>();
        for (String table : TABLES) {
            jdbc.queryForList("SELECT id::text AS id, (to_jsonb(t) - 'operation_date')::text AS row FROM " + table + " t")
                .forEach(row -> rows.put(table + ":" + row.get("id"), row.get("row")));
        }
        jdbc.queryForList("SELECT id::text AS id, current_balance::text AS balance FROM financial_accounts")
            .forEach(row -> rows.put("balance:" + row.get("id"), row.get("balance")));
        return rows;
    }

    private Map<LocalDate, BigDecimal> salesByDay() {
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (Object[] row : saleRepository.sumByDayAndType(OCT_28, OCT_31)) {
            totals.merge(LocalDate.parse((String) row[0]), (BigDecimal) row[4], BigDecimal::add);
        }
        return totals;
    }

    private static BigDecimal delta(Map<LocalDate, BigDecimal> before, Map<LocalDate, BigDecimal> after, LocalDate day) {
        return after.getOrDefault(day, BigDecimal.ZERO).subtract(before.getOrDefault(day, BigDecimal.ZERO));
    }

    private LocalDate day(String table, UUID id) {
        return jdbc.queryForObject("SELECT operation_date FROM " + table + " WHERE id = ?", LocalDate.class, id);
    }

    /** A La Paz local date and time ("2026-10-29T09:10"). */
    private static Instant at(String laPaz) {
        return java.time.LocalDateTime.parse(laPaz).atZone(java.time.ZoneId.of("America/La_Paz")).toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static LocalDate storeDay(Instant instant) {
        return instant.atZone(java.time.ZoneId.of("America/La_Paz")).toLocalDate();
    }

    private UUID account(String name) {
        return jdbc.queryForObject("INSERT INTO financial_accounts (name, initial_balance, current_balance, created_by) "
            + "VALUES (?, 0, 100, 'it') RETURNING id", UUID.class, name + " " + tag);
    }

    // As V21 left it: the day it was opened
    private UUID session(long number, String status, Instant openedAt, boolean historical) {
        UUID importId = historical ? jdbc.queryForObject("INSERT INTO historical_imports (cutoff_date, imported_by) "
            + "VALUES ('2026-10-31', ?) RETURNING id", UUID.class, tag) : null;
        boolean closed = status.equals("CERRADA");
        return jdbc.queryForObject("INSERT INTO cash_sessions (session_number, status, opening_amount, opened_by, opened_at, "
                + "operation_date, closing_amount, closed_by, closed_at, expected_cash, difference, historical_import_id) "
                + "VALUES (?, ?, 100, ?, ?, ?, ?, ?, ?, 100, ?, ?) RETURNING id",
            UUID.class, number, status, tag, ts(openedAt), storeDay(openedAt),
            closed ? new BigDecimal("98.00") : null, closed ? tag : null,
            closed ? ts(openedAt.plusSeconds(23 * 3600)) : null, closed ? new BigDecimal("-2.00") : null, importId);
    }

    // As V21 left it: the day it was recorded
    private UUID sale(UUID session, String total, Instant createdAt, String status) {
        return jdbc.queryForObject("INSERT INTO sales (sale_number, cash_session_id, payment_method, total_amount, "
                + "subtotal_amount, amount_cash, status, created_by, created_at, operation_date) "
                + "VALUES (?, ?, 'EFECTIVO', ?::numeric, ?::numeric, ?::numeric, ?, ?, ?, ?) RETURNING id",
            UUID.class, tag + "-" + UUID.randomUUID().toString().substring(0, 8), session, total, total, total, status,
            tag, ts(createdAt), storeDay(createdAt));
    }

    private UUID movement(String type, UUID account, UUID session, UUID sale, UUID category, String status, Instant date) {
        return jdbc.queryForObject("INSERT INTO financial_transactions (type, source_account_id, category_id, amount, "
                + "transaction_date, operation_date, description, status, cash_session_id, sale_id, created_by) "
                + "VALUES (?, ?, ?, 10, ?, ?, 'IT', ?, ?, ?, ?) RETURNING id",
            UUID.class, type, account, category, ts(date), storeDay(date), status, session, sale, tag);
    }
}
