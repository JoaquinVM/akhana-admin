package com.akhana.akhana_admin.integration;

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
 * FIN-11: the one-time correction of V28 on historical cash session #142. Flyway already ran it on this database, so
 * each test seeds its own rows and runs the SQL again inside the test transaction, which is rolled back at the end.
 */
@SpringBootTest
@Transactional
class CashSession142IncomesMigrationIntegrationTest {

    private static final String FIX = "db/migration/V28__cash_session_142_other_incomes.sql";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private String tag;
    private UUID caja;
    private UUID otherIncomeCategory;
    private UUID adjustmentCategory;

    @BeforeEach
    void setUp() {
        tag = "it-142-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.execute("DROP INDEX uq_cash_sessions_operation_date");
        jdbc.update("UPDATE cash_sessions SET session_number = session_number + 1000000000 WHERE session_number = 142");
        caja = jdbc.queryForObject("INSERT INTO financial_accounts (name, initial_balance, current_balance, created_by) "
            + "VALUES (?, 0, 100, 'it') RETURNING id", UUID.class, "Caja IT " + tag);
        List<UUID> categories = jdbc.queryForList("SELECT id FROM financial_categories ORDER BY name LIMIT 2", UUID.class);
        otherIncomeCategory = categories.get(0);
        adjustmentCategory = categories.get(1);
    }

    @Test
    @DisplayName("FIN-11: agrega los tres ingresos, reemplaza el sobrante por Bs 455,40 y recalcula la caja; "
        + "la caja final, el saldo y el total de ingresos del día no cambian")
    void addsTheIncomesAndReducesTheSurplus() {
        UUID importId = historicalImport();
        UUID session = session(importId, "780.40");
        UUID surplus = movement(importId, adjustmentCategory, "780.40", "Sobrante");
        movement(importId, otherIncomeCategory, "579.00", "Fit Mit Tia Neida");
        BigDecimal incomeBefore = activeIncome(importId);

        runFix();

        Map<String, Object> row = jdbc.queryForMap("SELECT historical_other_income, expected_cash, difference, closing_amount "
            + "FROM cash_sessions WHERE id = ?", session);
        assertThat((BigDecimal) row.get("historical_other_income")).isEqualByComparingTo("904.00");
        assertThat((BigDecimal) row.get("expected_cash")).isEqualByComparingTo("7182.50");
        assertThat((BigDecimal) row.get("difference")).isEqualByComparingTo("455.40");
        assertThat((BigDecimal) row.get("closing_amount")).isEqualByComparingTo("7637.90");

        assertThat(amountOf(importId, otherIncomeCategory, "Tia Neida Vida Amazonica")).isEqualByComparingTo("161.00");
        assertThat(amountOf(importId, otherIncomeCategory, "Tia Neida 6 brownies")).isEqualByComparingTo("84.00");
        assertThat(amountOf(importId, otherIncomeCategory, "Tia Neida 2 aceites de coco")).isEqualByComparingTo("80.00");
        assertThat(amountOf(importId, adjustmentCategory, "Sobrante")).isEqualByComparingTo("455.40");

        Map<String, Object> voided = jdbc.queryForMap("SELECT status, void_reason, voided_at FROM financial_transactions "
            + "WHERE id = ?", surplus);
        assertThat(voided.get("status")).isEqualTo("ANULADO");
        assertThat(voided.get("void_reason")).isNotNull();
        assertThat(voided.get("voided_at")).isNotNull();

        assertThat(activeIncome(importId)).isEqualByComparingTo(incomeBefore);
        assertThat(jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, caja))
            .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transactions WHERE historical_import_id = ? "
            + "AND cash_session_id IS NOT NULL", Long.class, importId)).isZero();
    }

    @Test
    @DisplayName("FIN-11: si la caja no tiene los montos importados, no cambia nada")
    void leavesASessionWithOtherAmountsAlone() {
        UUID importId = historicalImport();
        UUID session = session(importId, "100.00");
        movement(importId, adjustmentCategory, "780.40", "Sobrante");
        movement(importId, otherIncomeCategory, "579.00", "Fit Mit Tia Neida");

        runFix();

        assertThat(jdbc.queryForObject("SELECT difference FROM cash_sessions WHERE id = ?", BigDecimal.class, session))
            .isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transactions WHERE historical_import_id = ?",
            Long.class, importId)).isEqualTo(2L);
    }

    private void runFix() {
        // The connection of the test transaction, so everything is rolled back
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(FIX));
    }

    private UUID historicalImport() {
        return jdbc.queryForObject("INSERT INTO historical_imports (cutoff_date, imported_by) "
            + "VALUES ('2026-10-27', ?) RETURNING id", UUID.class, tag);
    }

    private UUID session(UUID importId, String difference) {
        return jdbc.queryForObject("INSERT INTO cash_sessions (session_number, status, opening_amount, opened_by, opened_at, "
                + "operation_date, closing_amount, closed_by, closed_at, expected_cash, difference, "
                + "historical_other_income, historical_import_id) "
                + "VALUES (142, 'CERRADA', 6273.00, ?, '2026-10-23 13:00+00', '2026-10-23', 7637.90, ?, "
                + "'2026-10-24 01:00+00', 6857.50, ?::numeric, 579.00, ?) RETURNING id",
            UUID.class, tag, tag, difference, importId);
    }

    private UUID movement(UUID importId, UUID category, String amount, String description) {
        return jdbc.queryForObject("INSERT INTO financial_transactions (historical_import_id, type, source_account_id, "
                + "category_id, amount, transaction_date, operation_date, description, created_by) "
                + "VALUES (?, 'INGRESO_MANUAL', ?, ?, ?::numeric, '2026-10-23 16:00+00', '2026-10-23', ?, ?) RETURNING id",
            UUID.class, importId, caja, category, amount, description, tag);
    }

    private BigDecimal activeIncome(UUID importId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM financial_transactions "
            + "WHERE historical_import_id = ? AND status = 'ACTIVO' AND type = 'INGRESO_MANUAL'", BigDecimal.class, importId);
    }

    private BigDecimal amountOf(UUID importId, UUID category, String description) {
        return jdbc.queryForObject("SELECT amount FROM financial_transactions WHERE historical_import_id = ? "
                + "AND category_id = ? AND description = ? AND status = 'ACTIVO' AND operation_date = '2026-10-23'",
            BigDecimal.class, importId, category, description);
    }
}
