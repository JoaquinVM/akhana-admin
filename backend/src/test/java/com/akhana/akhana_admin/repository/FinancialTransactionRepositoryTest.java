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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private void insert(String type, UUID source, UUID destination, String amount, String status, UUID session) {
        jdbc.update("INSERT INTO financial_transactions (type, source_account_id, destination_account_id, amount, transaction_date, "
                        + "description, status, cash_session_id, created_by) VALUES (?, ?, ?, ?, now(), 'IT', ?, ?, 'it-repo')",
                type, source, destination, new BigDecimal(amount), status, session);
    }
}
