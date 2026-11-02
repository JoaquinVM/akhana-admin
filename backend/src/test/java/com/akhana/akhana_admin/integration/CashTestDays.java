package com.akhana.akhana_admin.integration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * For the tests that run inside a transaction that is rolled back: whatever sessions this database has, they go far
 * into the past (rolled back too), so the sequence of days starts from the session each test seeds.
 */
final class CashTestDays {

    private CashTestDays() {
    }

    static void movePastSessionsAway(JdbcTemplate jdbc) {
        jdbc.update("UPDATE cash_sessions SET operation_date = operation_date - 40000");
    }

    /** A closed session of the system with no difference, the Caja balance equal to its closing. */
    static UUID seedClosedSession(JdbcTemplate jdbc, LocalDate day, String closing, String user) {
        UUID id = jdbc.queryForObject("INSERT INTO cash_sessions (status, opening_amount, opened_by, operation_date, "
                + "closing_amount, closed_by, closed_at, expected_cash, difference) "
                + "VALUES ('CERRADA', ?::numeric, ?, ?, ?::numeric, ?, now(), ?::numeric, 0) RETURNING id",
            UUID.class, closing, user, day, closing, user, closing);
        jdbc.update("UPDATE financial_accounts SET current_balance = ?::numeric WHERE is_cash_account = TRUE", closing);
        return id;
    }
}
