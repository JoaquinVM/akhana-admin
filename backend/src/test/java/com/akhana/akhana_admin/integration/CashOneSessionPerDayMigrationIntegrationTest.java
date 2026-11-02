package com.akhana.akhana_admin.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAJ-12 on deploy: V23 gives a full index without repeated days and a partial one with them, without moving data.
 * Flyway already ran it on this database, so each test drops the index, seeds its data and runs the whole file inside
 * the test transaction, which is rolled back at the end: nothing stays.
 */
@SpringBootTest
@Transactional
class CashOneSessionPerDayMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V23__cash_one_session_per_day.sql";
    private static final LocalDate DAY = LocalDate.of(1901, 3, 4);

    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void dropIndexAndUnrepeatDays() {
        jdbc.execute("DROP INDEX uq_cash_sessions_operation_date");
        // Whatever this database has, each session gets a day of its own, far from the seeded ones
        jdbc.update("UPDATE cash_sessions SET operation_date = DATE '1910-01-01' + session_number::int");
    }

    @Test
    @DisplayName("Sin días repetidos, el índice es completo y rechaza un día repetido")
    void withoutRepeatedDays_fullIndex() throws IOException {
        seedSession(DAY);

        runMigration();

        assertThat(indexDefinition()).doesNotContainIgnoringCase("WHERE");
        assertThatThrownBy(() -> seedSession(DAY)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Con días repetidos, el índice deja fuera hasta el último repetido, sin mover datos, y protege a las nuevas")
    void withRepeatedDays_partialIndexAndNoDataMoved() throws IOException {
        UUID first = seedSession(DAY);
        UUID second = seedSession(DAY);
        long lastRepeated = number(second);
        List<String> before = sessions();

        runMigration();

        assertThat(indexDefinition()).contains("WHERE").contains("session_number > " + lastRepeated);
        assertThat(sessions()).isEqualTo(before);
        assertThat(day(first)).isEqualTo(DAY);
        assertThat(day(second)).isEqualTo(DAY);

        LocalDate newDay = DAY.plusDays(1);
        seedSession(newDay);
        assertThatThrownBy(() -> seedSession(newDay)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // The whole file: splitting by ; would break the $$ block
    private void runMigration() throws IOException {
        jdbc.execute(StreamUtils.copyToString(new ClassPathResource(MIGRATION).getInputStream(), StandardCharsets.UTF_8));
    }

    private String indexDefinition() {
        return jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_cash_sessions_operation_date'", String.class);
    }

    private UUID seedSession(LocalDate day) {
        return jdbc.queryForObject("INSERT INTO cash_sessions (status, opening_amount, opened_by, operation_date) "
            + "VALUES ('CERRADA', 100, 'it-v23', ?) RETURNING id", UUID.class, day);
    }

    private long number(UUID session) {
        return jdbc.queryForObject("SELECT session_number FROM cash_sessions WHERE id = ?", Long.class, session);
    }

    private LocalDate day(UUID session) {
        return jdbc.queryForObject("SELECT operation_date FROM cash_sessions WHERE id = ?", LocalDate.class, session);
    }

    private List<String> sessions() {
        return jdbc.queryForList("SELECT id || '|' || session_number || '|' || operation_date || '|' || status "
            + "FROM cash_sessions ORDER BY session_number", String.class);
    }
}
