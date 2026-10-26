package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.FinancialTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class FinancialTransactionRepositoryTest {

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void testFilterTransactionsWithNullSearch() {
        Instant now = Instant.now();
        List<FinancialTransaction> result = financialTransactionRepository.filterTransactions(
                now.minusSeconds(86400 * 30),
                now,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(result);
    }

    @Test
    void testFilterTransactionsWithNonNullSearch() {
        Instant now = Instant.now();
        List<FinancialTransaction> result = financialTransactionRepository.filterTransactions(
                now.minusSeconds(86400 * 30),
                now,
                null,
                null,
                null,
                null,
                "venta"
        );
        assertNotNull(result);
    }

    @Test
    @Transactional
    void expectedCashSumsCountOnlyActiveCashMovementsOfTheSession() {
        List<UUID> cash = jdbc.queryForList("SELECT id FROM financial_accounts WHERE is_cash_account = TRUE", UUID.class);
        assumeTrue(cash.size() == 1, "La base no tiene la cuenta Caja");
        UUID caja = cash.get(0);
        UUID other = jdbc.queryForObject(
                "INSERT INTO financial_accounts (name, initial_balance, current_balance, created_by) VALUES (?, 0, 0, 'it-repo') RETURNING id",
                UUID.class, "IT efectivo " + UUID.randomUUID().toString().substring(0, 8));
        // Closed so it never collides with a real open session; rolled back at the end
        UUID session = jdbc.queryForObject(
                "INSERT INTO cash_sessions (status, opening_amount, opened_by) VALUES ('CERRADA', 100, 'it-repo') RETURNING id", UUID.class);
        UUID otherSession = jdbc.queryForObject(
                "INSERT INTO cash_sessions (status, opening_amount, opened_by) VALUES ('CERRADA', 100, 'it-repo') RETURNING id", UUID.class);

        insert("GASTO_MANUAL", caja, null, "40.00", "ACTIVO", session);
        insert("GASTO_MANUAL", caja, null, "15.00", "ANULADO", session);
        insert("TRANSFERENCIA", caja, other, "60.00", "ACTIVO", session);
        insert("TRANSFERENCIA", caja, other, "7.00", "ANULADO", session);
        insert("TRANSFERENCIA", other, caja, "25.00", "ACTIVO", session);
        insert("TRANSFERENCIA", other, caja, "9.00", "ANULADO", session);
        insert("GASTO_MANUAL", other, null, "33.00", "ACTIVO", session);
        insert("GASTO_MANUAL", caja, null, "11.00", "ACTIVO", otherSession);
        // Collections into Caja (DEU-06) add; sale incomes are counted through the session totals, not here
        insert("INGRESO_MANUAL", caja, null, "30.00", "ACTIVO", session);
        insert("INGRESO_MANUAL", caja, null, "5.00", "ANULADO", session);
        insert("INGRESO_MANUAL", other, null, "12.00", "ACTIVO", session);
        insert("VENTA_INGRESO", caja, null, "50.00", "ACTIVO", session);

        assertEquals(0, new BigDecimal("100.00").compareTo(financialTransactionRepository.sumActiveCashOutflowsBySessionId(session)));
        assertEquals(0, new BigDecimal("55.00").compareTo(financialTransactionRepository.sumActiveCashInflowsBySessionId(session)));
    }

    @Test
    @Transactional
    void movementsBesidesOpeningBalanceIgnoreOnlyTheOpeningBalance() {
        UUID account = newAccount("IT corte ");
        UUID other = newAccount("IT otra ");
        insert("SALDO_INICIAL", account, null, "0.00", "ACTIVO", null);
        assertFalse(financialTransactionRepository.existsMovementsBesidesOpeningBalance(account));

        insert("TRANSFERENCIA", other, account, "5.00", "ANULADO", null);
        assertTrue(financialTransactionRepository.existsMovementsBesidesOpeningBalance(account));

        UUID third = newAccount("IT gasto ");
        insert("SALDO_INICIAL", third, null, "0.00", "ACTIVO", null);
        insert("GASTO_MANUAL", third, null, "1.00", "ACTIVO", null);
        assertTrue(financialTransactionRepository.existsMovementsBesidesOpeningBalance(third));
    }

    @Test
    @Transactional
    void reportSumsGroupActiveMovementsByStoreMonthCategoryAndTransferPair() {
        Instant from = Instant.parse("2001-08-01T04:00:00Z");
        Instant to = Instant.parse("2001-09-01T04:00:00Z");
        UUID caja = newAccount("IT caja ");
        UUID efectivo = newAccount("IT efectivo ");
        UUID venta = newCategory("IT Venta ", "INGRESO", null);
        UUID comida = newCategory("IT Comida ", "GASTO", null);
        UUID disabled = newCategory("IT Vieja ", "GASTO", Instant.parse("2001-08-02T00:00:00Z"));
        // 23:30 of 31-08 in Bolivia is 03:30 of 01-09 UTC: still August
        Instant lastNight = Instant.parse("2001-09-01T03:30:00Z");
        insertAt("VENTA_INGRESO", caja, null, venta, "100.00", "ACTIVO", lastNight);
        insertAt("INGRESO_MANUAL", efectivo, null, venta, "20.00", "ACTIVO", lastNight);
        insertAt("VENTA_INGRESO", caja, null, venta, "999.00", "ANULADO", lastNight);
        insertAt("GASTO_MANUAL", caja, null, comida, "30.00", "ACTIVO", lastNight);
        insertAt("GASTO_MANUAL", caja, null, disabled, "5.00", "ACTIVO", lastNight);
        insertAt("SALDO_INICIAL", caja, null, null, "500.00", "ACTIVO", lastNight);
        insertAt("TRANSFERENCIA", caja, efectivo, null, "60.00", "ACTIVO", lastNight);
        insertAt("TRANSFERENCIA", caja, efectivo, null, "15.00", "ACTIVO", lastNight);
        insertAt("TRANSFERENCIA", caja, efectivo, null, "7.00", "ANULADO", lastNight);
        insertAt("GASTO_MANUAL", caja, null, comida, "40.00", "ACTIVO", to);

        List<Object[]> categories = financialTransactionRepository.sumByMonthAndCategory(from, to);
        assertEquals(3, categories.size());
        assertEquals(0, new BigDecimal("120.00").compareTo(sumOf(categories, "INGRESO", venta)));
        assertEquals(0, new BigDecimal("30.00").compareTo(sumOf(categories, "GASTO", comida)));
        assertEquals(0, new BigDecimal("5.00").compareTo(sumOf(categories, "GASTO", disabled)));
        categories.forEach(row -> assertEquals("2001-08", row[0]));

        List<Object[]> transfers = financialTransactionRepository.sumTransfersByMonth(from, to);
        assertEquals(1, transfers.size());
        assertEquals("2001-08", transfers.getFirst()[0]);
        assertEquals(caja, transfers.getFirst()[1]);
        assertEquals(efectivo, transfers.getFirst()[3]);
        assertEquals(0, new BigDecimal("75.00").compareTo((BigDecimal) transfers.getFirst()[5]));
    }

    private static BigDecimal sumOf(List<Object[]> rows, String direction, UUID category) {
        return rows.stream().filter(row -> direction.equals(row[1]) && category.equals(row[2]))
                .map(row -> (BigDecimal) row[4]).findFirst().orElseThrow();
    }

    private UUID newCategory(String prefix, String type, Instant deletedAt) {
        return jdbc.queryForObject(
                "INSERT INTO financial_categories (name, type, deleted_at) VALUES (?, ?, ?) RETURNING id",
                UUID.class, prefix + UUID.randomUUID().toString().substring(0, 8), type,
                deletedAt != null ? java.sql.Timestamp.from(deletedAt) : null);
    }

    private void insertAt(String type, UUID source, UUID destination, UUID category, String amount, String status,
                          Instant date) {
        jdbc.update("INSERT INTO financial_transactions (type, source_account_id, destination_account_id, category_id, amount, "
                        + "transaction_date, description, status, created_by) VALUES (?, ?, ?, ?, ?, ?, 'IT', ?, 'it-repo')",
                type, source, destination, category, new BigDecimal(amount), java.sql.Timestamp.from(date), status);
    }

    private UUID newAccount(String prefix) {
        return jdbc.queryForObject(
                "INSERT INTO financial_accounts (name, initial_balance, current_balance, created_by) VALUES (?, 0, 0, 'it-repo') RETURNING id",
                UUID.class, prefix + UUID.randomUUID().toString().substring(0, 8));
    }

    private void insert(String type, UUID source, UUID destination, String amount, String status, UUID session) {
        jdbc.update("INSERT INTO financial_transactions (type, source_account_id, destination_account_id, amount, transaction_date, "
                        + "description, status, cash_session_id, created_by) VALUES (?, ?, ?, ?, now(), 'IT', ?, ?, 'it-repo')",
                type, source, destination, new BigDecimal(amount), status, session);
    }
}
