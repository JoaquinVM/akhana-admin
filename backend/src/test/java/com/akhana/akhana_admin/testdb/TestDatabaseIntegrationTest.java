package com.akhana.akhana_admin.testdb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Instant;

import static com.akhana.akhana_admin.testdb.TestDatabaseContextCustomizerFactory.requireTestDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The tests run on their own database, emptied at the start of the run, and never on one without a test name. */
@SpringBootTest
class TestDatabaseIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Las pruebas usan la base de TEST_DB_NAME (por defecto akhana_test), nunca la base akhana")
    void runsOnTheTestDatabase() {
        String current = jdbc.queryForObject("SELECT current_database()", String.class);
        String expected = System.getenv().getOrDefault("TEST_DB_NAME", "akhana_test");
        assertThat(current).isEqualTo(expected).isNotEqualTo("akhana").matches(".+_test(_.+)?");
    }

    @Test
    @DisplayName("La corrida limpió la base antes de migrarla")
    void theRunStartedFromAnEmptyDatabase() {
        assertThat(TestDatabaseContextCustomizerFactory.preparedThisRun()).isTrue();
        // Flyway clean drops the history, so every migration was installed again after this JVM started
        Timestamp firstMigration = jdbc.queryForObject("SELECT MIN(installed_on) FROM flyway_schema_history", Timestamp.class);
        Instant jvmStart = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        assertThat(firstMigration.toInstant()).isAfter(jvmStart.minusSeconds(5));
    }

    @Test
    @DisplayName("La guarda solo acepta bases que terminan en _test o _test_<algo>")
    void theGuardOnlyAcceptsTestNames() {
        assertThat(requireTestDatabase("jdbc:postgresql://localhost:5432/akhana_test")).isEqualTo("akhana_test");
        assertThat(requireTestDatabase("jdbc:postgresql://localhost:5432/akhana_test_fix_cash?ssl=false"))
            .isEqualTo("akhana_test_fix_cash");
        for (String url : new String[] {"jdbc:postgresql://localhost:5432/akhana", "jdbc:postgresql://localhost:5432/akhana_testing",
            "jdbc:postgresql://localhost:5432/test_akhana", "jdbc:postgresql://localhost:5432/akhana_local_main",
            "jdbc:h2:mem:akhana_test", null}) {
            assertThatThrownBy(() -> requireTestDatabase(url)).isInstanceOf(IllegalStateException.class);
        }
    }
}
