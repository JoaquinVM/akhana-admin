package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.model.CashSession;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
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

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAJ-06: the one-time correction of V35 on closed cash session #161. Flyway already ran it on this database, so each
 * test seeds the production state of 18/11/2026 and runs the SQL again inside the test transaction, which is rolled
 * back at the end.
 */
@SpringBootTest
@Transactional
class CashSession161DeliPaymentMigrationIntegrationTest {

    private static final String FIX = "db/migration/V35__cash_session_161_deli_payment.sql";
    private static final String PAYMENT_TIME = "2026-11-17 18:26:42.532084+00";
    private static final String CLOSING_TIME = "2026-11-17 18:20:37.701384+00";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private CashSessionRepository cashSessionRepository;
    @Autowired private ExpectedCashCalculator expectedCashCalculator;

    private String tag;
    private UUID caja;
    private UUID paymentCategory;
    private UUID shortageCategory;
    private UUID surplusCategory;

    @BeforeEach
    void setUp() {
        tag = "it-161-" + UUID.randomUUID().toString().substring(0, 8);
        // Out of the way, if this database has them: negative numbers are outside the one-session-per-day index
        jdbc.update("UPDATE cash_sessions SET session_number = -session_number WHERE session_number IN (161, 162)");
        jdbc.update("UPDATE purchases SET purchase_number = purchase_number || ? WHERE purchase_number = 'PED-000053'",
            "-" + tag);
        List<UUID> cash = jdbc.queryForList("SELECT id FROM financial_accounts WHERE is_cash_account", UUID.class);
        caja = cash.isEmpty()
            ? jdbc.queryForObject("INSERT INTO financial_accounts (name, initial_balance, current_balance, "
                + "is_cash_account, created_by) VALUES (?, 0, 5121.60, TRUE, 'it') RETURNING id", UUID.class, "Caja " + tag)
            : cash.get(0);
        paymentCategory = systemCategory("PURCHASE_PAYMENT");
        shortageCategory = systemCategory("CASH_SHORTAGE");
        surplusCategory = systemCategory("CASH_SURPLUS");
    }

    @Test
    @DisplayName("CAJ-06: pasa el pago de Bs 360 de PED-000053 a la caja #161, anula su faltante de Bs 260 y deja un "
        + "sobrante de Bs 100; el saldo de Caja, lo contado, los cortes y la caja #162 no cambian")
    void movesTheDeliPaymentToMondaysSession() {
        Production state = productionState("4233.20", "-260.00", "0.00");
        BigDecimal balanceBefore = cashBalance();
        Map<String, Object> session162Before = row("cash_sessions", state.session162());
        Map<String, Object> voidedBefore = row("financial_transactions", state.voidedPayment());
        Map<String, Object> cutBefore = row("cash_denomination_cuts", state.cut());

        runFix();

        // Rule 1: the payment, like one from Caja in the app
        Map<String, Object> payment = jdbc.queryForMap("SELECT id, type, source_account_id, category_id, amount, "
                + "operation_date::text AS day, created_by, notes, transaction_date = ?::timestamptz AS at_voided_time "
                + "FROM financial_transactions WHERE cash_session_id = ? AND purchase_id = ? AND status = 'ACTIVO'",
            PAYMENT_TIME, state.session161(), state.purchase());
        assertThat(payment.get("type")).isEqualTo("GASTO_MANUAL");
        assertThat(payment.get("source_account_id")).isEqualTo(caja);
        assertThat(payment.get("category_id")).isEqualTo(paymentCategory);
        assertThat((BigDecimal) payment.get("amount")).isEqualByComparingTo("360.00");
        assertThat(payment.get("day")).isEqualTo("2026-11-16");
        assertThat(payment.get("at_voided_time")).isEqualTo(true);
        assertThat(payment.get("created_by")).isEqualTo("SYSTEM");
        assertThat(payment.get("notes")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT description FROM financial_transactions WHERE id = ?", String.class,
            payment.get("id"))).isEqualTo("Pago pedido PED-000053");

        Map<String, Object> purchasePayment = jdbc.queryForMap("SELECT account_id, amount, operation_date::text AS day, "
                + "payment_date = ?::timestamptz AS at_voided_time, created_by FROM purchase_payments "
                + "WHERE financial_transaction_id = ? AND status = 'ACTIVO'",
            PAYMENT_TIME, payment.get("id"));
        assertThat(purchasePayment.get("account_id")).isEqualTo(caja);
        assertThat((BigDecimal) purchasePayment.get("amount")).isEqualByComparingTo("360.00");
        assertThat(purchasePayment.get("day")).isEqualTo("2026-11-16");
        assertThat(purchasePayment.get("at_voided_time")).isEqualTo(true);
        assertThat(purchasePayment.get("created_by")).isEqualTo("SYSTEM");

        // Rule 2: the purchase is paid
        assertThat(jdbc.queryForObject("SELECT total_amount - paid_amount FROM purchases WHERE id = ?", BigDecimal.class,
            state.purchase())).isEqualByComparingTo("0.00");

        // Rule 3 and 6: the shortage is voided with a reason, nothing is deleted, and the surplus comes in
        Map<String, Object> shortage = jdbc.queryForMap("SELECT status, voided_by, voided_at, void_reason "
            + "FROM financial_transactions WHERE id = ?", state.shortage());
        assertThat(shortage.get("status")).isEqualTo("ANULADO");
        assertThat(shortage.get("voided_by")).isEqualTo("SYSTEM");
        assertThat(shortage.get("voided_at")).isNotNull();
        assertThat(shortage.get("void_reason")).isNotNull();
        Map<String, Object> surplus = jdbc.queryForMap("SELECT type, source_account_id, category_id, amount, "
                + "operation_date::text AS day, created_by, transaction_date = ?::timestamptz AS at_closing_time "
                + "FROM financial_transactions WHERE cash_session_id = ? AND status = 'ACTIVO' "
                + "AND description = 'Sobrante al cierre de caja #161'",
            CLOSING_TIME, state.session161());
        assertThat(surplus.get("type")).isEqualTo("INGRESO_MANUAL");
        assertThat(surplus.get("source_account_id")).isEqualTo(caja);
        assertThat(surplus.get("category_id")).isEqualTo(surplusCategory);
        assertThat((BigDecimal) surplus.get("amount")).isEqualByComparingTo("100.00");
        assertThat(surplus.get("day")).isEqualTo("2026-11-16");
        assertThat(surplus.get("at_closing_time")).isEqualTo(true);
        assertThat(surplus.get("created_by")).isEqualTo("SYSTEM");

        // Rule 4: the session, with the expected cash the app calculates
        Map<String, Object> session = row("cash_sessions", state.session161());
        assertThat((BigDecimal) session.get("expected_cash")).isEqualByComparingTo("4133.20");
        assertThat((BigDecimal) session.get("difference")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) session.get("opening_amount")).isEqualByComparingTo("4067.70");
        assertThat((BigDecimal) session.get("total_sales_cash")).isEqualByComparingTo("2389.00");
        assertThat((BigDecimal) session.get("closing_amount")).isEqualByComparingTo("4233.20");
        assertThat(session.get("status")).isEqualTo("CERRADA");
        CashSession entity = cashSessionRepository.findById(state.session161()).orElseThrow();
        assertThat(expectedCashCalculator.calculate(entity)).isEqualByComparingTo("4133.20");
        assertThat(row("cash_denomination_cuts", state.cut())).isEqualTo(cutBefore);

        // Rule 5: Caja's balance, session #162 and its voided payment stay as they were
        assertThat(cashBalance()).isEqualByComparingTo(balanceBefore);
        assertThat(row("cash_sessions", state.session162())).isEqualTo(session162Before);
        assertThat(row("financial_transactions", state.voidedPayment())).isEqualTo(voidedBefore);
    }

    @Test
    @DisplayName("CAJ-06: si la caja #161 no tiene los montos de producción, no cambia nada")
    void leavesASessionWithOtherAmountsAlone() {
        Production state = productionState("4243.20", "-250.00", "0.00");
        Map<String, Object> before = snapshot(state);

        runFix();

        assertThat(snapshot(state)).isEqualTo(before);
    }

    @Test
    @DisplayName("CAJ-06: si PED-000053 ya tiene algo pagado, no cambia nada")
    void leavesAPaidPurchaseAlone() {
        Production state = productionState("4233.20", "-260.00", "360.00");
        Map<String, Object> before = snapshot(state);

        runFix();

        assertThat(snapshot(state)).isEqualTo(before);
    }

    @Test
    @DisplayName("CAJ-06: corrida dos veces, la corrección se aplica una sola vez")
    void appliesOnlyOnce() {
        Production state = productionState("4233.20", "-260.00", "0.00");
        runFix();
        Map<String, Object> afterFirst = snapshot(state);

        runFix();

        assertThat(snapshot(state)).isEqualTo(afterFirst);
        assertThat(afterFirst.get("activeInSession161")).isEqualTo(5L);
    }

    /** Ids of the seeded rows. */
    private record Production(UUID session161, UUID session162, UUID shortage, UUID purchase, UUID voidedPayment,
                              UUID cut) {}

    // Sessions #161 and #162, their Caja movements and PED-000053 as production had them on 18/11/2026
    private Production productionState(String closingAmount, String difference, String paidAmount) {
        UUID session161 = jdbc.queryForObject("INSERT INTO cash_sessions (session_number, status, opening_amount, "
                + "opened_by, opened_at, operation_date, closing_amount, closed_by, closed_at, total_sales_cash, "
                + "total_sales_qr, total_sales, expected_cash, difference) VALUES (161, 'CERRADA', 4067.70, ?, "
                + "'2026-11-16 16:39:36+00', '2026-11-16', ?::numeric, ?, ?::timestamptz, 2389.00, 1895.00, 4284.00, "
                + "4493.20, ?::numeric) RETURNING id",
            UUID.class, tag, closingAmount, tag, CLOSING_TIME, difference);
        UUID session162 = jdbc.queryForObject("INSERT INTO cash_sessions (session_number, status, opening_amount, "
                + "opened_by, opened_at, operation_date, closing_amount, closed_by, closed_at, total_sales_cash, "
                + "total_sales_qr, total_sales, expected_cash, difference) VALUES (162, 'CERRADA', 4233.20, ?, "
                + "'2026-11-17 18:25:39+00', '2026-11-17', 5121.60, ?, '2026-11-17 23:45:03+00', 1485.00, 388.00, "
                + "1873.00, 5121.60, 0.00) RETURNING id",
            UUID.class, tag, tag);
        UUID cut = jdbc.queryForObject("INSERT INTO cash_denomination_cuts (cash_session_id, denomination, "
            + "cash_quantity, reserve_quantity, subtotal) VALUES (?, 200.00, 21, 0, 4200.00) RETURNING id", UUID.class, session161);

        UUID supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "Deli " + tag, tag, tag);
        UUID purchase = jdbc.queryForObject("INSERT INTO purchases (purchase_number, supplier_id, purchase_date, "
                + "items_total, total_amount, paid_amount, created_by) VALUES ('PED-000053', ?, '2026-11-16', 360.00, "
                + "360.00, ?::numeric, ?) RETURNING id",
            UUID.class, supplier, paidAmount, tag);

        // #161: the expenses that make its expected cash 4493.20 and the closing shortage
        movement(session161, "GASTO_MANUAL", paymentCategory, "1548.50", "2026-11-16 16:44:35+00", "2026-11-16",
            "Pago pedido PED-000031", null, "ACTIVO");
        movement(session161, "GASTO_MANUAL", paymentCategory, "387.00", "2026-11-16 18:30:43+00", "2026-11-16",
            "Pago pedido PED-000052", null, "ACTIVO");
        movement(session161, "GASTO_MANUAL", paymentCategory, "28.00", "2026-11-16 21:49:31+00", "2026-11-16",
            "Almuerzo", null, "ACTIVO");
        UUID shortage = movement(session161, "GASTO_MANUAL", shortageCategory, "260.00", CLOSING_TIME, "2026-11-16",
            "Faltante al cierre de caja #161", null, "ACTIVO");

        // #162: the payment of PED-000053, voided to load it in the previous session
        UUID voidedPayment = movement(session162, "GASTO_MANUAL", paymentCategory, "360.00", PAYMENT_TIME, "2026-11-17",
            "Pago pedido PED-000053", purchase, "ANULADO");
        jdbc.update("INSERT INTO purchase_payments (purchase_id, account_id, amount, payment_date, operation_date, "
                + "financial_transaction_id, status, created_by, voided_by, voided_at, void_reason) VALUES (?, ?, 360.00, "
                + "?::timestamptz, '2026-11-17', ?, 'ANULADO', ?, ?, '2026-11-17 23:40:41+00', "
                + "'CARGAR EN LA CAJA ANTERIOR 16/11/26')",
            purchase, caja, PAYMENT_TIME, voidedPayment, tag, tag);
        return new Production(session161, session162, shortage, purchase, voidedPayment, cut);
    }

    private UUID movement(UUID session, String type, UUID category, String amount, String at, String day,
                          String description, UUID purchase, String status) {
        boolean voided = "ANULADO".equals(status);
        return jdbc.queryForObject("INSERT INTO financial_transactions (type, source_account_id, category_id, amount, "
                + "transaction_date, operation_date, description, status, cash_session_id, purchase_id, created_by, "
                + "voided_by, voided_at, void_reason) VALUES (?, ?, ?, ?::numeric, ?::timestamptz, ?::date, ?, ?, ?, ?, ?, "
                + "?, ?::timestamptz, ?) RETURNING id",
            UUID.class, type, caja, category, amount, at, day, description, status, session, purchase, tag,
            voided ? tag : null, voided ? "2026-11-17 23:40:41+00" : null,
            voided ? "CARGAR EN LA CAJA ANTERIOR 16/11/26" : null);
    }

    private Map<String, Object> snapshot(Production state) {
        return Map.of(
            "session161", row("cash_sessions", state.session161()),
            "session162", row("cash_sessions", state.session162()),
            "purchase", row("purchases", state.purchase()),
            "activeInSession161", jdbc.queryForObject("SELECT count(*) FROM financial_transactions "
                + "WHERE cash_session_id = ? AND status = 'ACTIVO'", Long.class, state.session161()),
            "shortage", row("financial_transactions", state.shortage()),
            "purchasePayments", jdbc.queryForObject("SELECT count(*) FROM purchase_payments WHERE purchase_id = ?",
                Long.class, state.purchase()),
            "cashBalance", cashBalance());
    }

    private Map<String, Object> row(String table, UUID id) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
    }

    private BigDecimal cashBalance() {
        return jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, caja);
    }

    private UUID systemCategory(String systemKey) {
        return jdbc.queryForObject("SELECT id FROM financial_categories WHERE system_key = ? AND deleted_at IS NULL",
            UUID.class, systemKey);
    }

    private void runFix() {
        // The connection of the test transaction, so everything is rolled back
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(FIX));
    }
}
