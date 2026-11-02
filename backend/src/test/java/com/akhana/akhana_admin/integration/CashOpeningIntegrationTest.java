package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.CashSessionSummaryResponse;
import com.akhana.akhana_admin.dto.OpenCashRequest;
import com.akhana.akhana_admin.model.CashSession;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

/** CAJ-12, CAJ-13 against PostgreSQL. Each test rolls back, except the one that needs a real rollback of the service. */
@SpringBootTest
@Transactional
class CashOpeningIntegrationTest {

    private static final String USER = "it-opening";

    @Autowired private CashSessionService cashSessionService;
    @Autowired private CashSessionRepository cashSessionRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private FinancialCategoryRepository financialCategoryRepository;

    /** A Tuesday at least a week ago: the session of the Friday before it leaves Saturday and Monday missing. */
    private LocalDate pastTuesday() {
        return StoreFormat.today().minusDays(7).with(TemporalAdjusters.previous(DayOfWeek.TUESDAY));
    }

    private void requireNoOpenSession() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assumeTrue(open != null && open == 0, "La base ya tiene una caja abierta");
    }

    @Test
    @DisplayName("CAJ-12: la caja queda con el día elegido, la hora real y el ajuste de apertura en ese día")
    void opensWithTheChosenDay() {
        requireNoOpenSession();
        CashTestDays.movePastSessionsAway(jdbc);
        LocalDate tuesday = pastTuesday();
        CashTestDays.seedClosedSession(jdbc, tuesday.minusDays(1), "100.00", USER);

        CashSessionSummaryResponse opened = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal("90.00"), "Carga atrasada", tuesday, null, null), USER);
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT operation_date FROM cash_sessions WHERE id = ?", LocalDate.class, opened.id()))
            .isEqualTo(tuesday);
        assertThat(StoreFormat.storeDay(opened.openedAt())).isEqualTo(StoreFormat.today());
        assertThat(jdbc.queryForObject("SELECT operation_date FROM financial_transactions "
            + "WHERE cash_session_id = ? AND description LIKE 'Diferencia al abrir%'", LocalDate.class, opened.id())).isEqualTo(tuesday);
    }

    @Test
    @DisplayName("CAJ-13: al confirmar, los días quedan marcados con comentario, quién y cuándo, y la caja se abre")
    void confirmedDaysAreMarkedWithTheOpening() {
        requireNoOpenSession();
        CashTestDays.movePastSessionsAway(jdbc);
        LocalDate tuesday = pastTuesday();
        LocalDate friday = tuesday.minusDays(4);
        LocalDate saturday = tuesday.minusDays(3);
        LocalDate monday = tuesday.minusDays(1);
        CashTestDays.seedClosedSession(jdbc, friday, "100.00", USER);

        CashSessionSummaryResponse opened = cashSessionService.openSession(new OpenCashRequest(new BigDecimal("100.00"),
            "Carga atrasada", tuesday, List.of(saturday, monday), "Feriado"), USER);
        entityManager.flush();

        assertThat(jdbc.queryForList("SELECT no_attention_date FROM cash_no_attention_days WHERE cash_session_id = ? "
            + "ORDER BY 1", LocalDate.class, opened.id())).containsExactly(saturday, monday);
        assertThat(jdbc.queryForList("SELECT DISTINCT comment || '|' || marked_by FROM cash_no_attention_days "
            + "WHERE cash_session_id = ?", String.class, opened.id())).containsExactly("Feriado|" + USER);
    }

    @Test
    @DisplayName("CAJ-13: un día marcado no se puede abrir, aunque se cierre la caja que lo marcó")
    void markedDayCannotBeOpened() {
        requireNoOpenSession();
        CashTestDays.movePastSessionsAway(jdbc);
        LocalDate tuesday = pastTuesday();
        CashTestDays.seedClosedSession(jdbc, tuesday.minusDays(4), "100.00", USER);
        cashSessionService.openSession(new OpenCashRequest(new BigDecimal("100.00"), "Carga", tuesday,
            List.of(tuesday.minusDays(3), tuesday.minusDays(1)), "Feriado"), USER);
        entityManager.flush();
        jdbc.update("UPDATE cash_sessions SET status = 'CERRADA', closing_amount = 100, closed_at = now(), difference = 0 WHERE status = 'ABIERTA'");
        entityManager.clear();

        assertThatThrownBy(() -> cashSessionService.openSession(new OpenCashRequest(new BigDecimal("100.00"), "Carga",
                tuesday.minusDays(1), null, null), USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("El día debe ser posterior al de la última caja");
    }

    @Test
    @DisplayName("CAJ-13: la base no acepta marcar un domingo ni marcar dos veces un día")
    void databaseRejectsSundayAndRepeatedMark() {
        requireNoOpenSession();
        LocalDate tuesday = pastTuesday();
        UUID session = CashTestDays.seedClosedSession(jdbc, LocalDate.of(1905, 1, 2), "100.00", USER);
        String mark = "INSERT INTO cash_no_attention_days (no_attention_date, comment, cash_session_id, marked_by) VALUES (?, 'x', ?, ?)";

        jdbc.update(mark, tuesday, session, USER);

        assertThatThrownBy(() -> jdbc.update(mark, tuesday.minusDays(2), session, USER))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CAJ-12: la base no acepta dos cajas con el mismo día")
    void databaseRejectsTwoSessionsOfADay() {
        LocalDate day = LocalDate.of(1905, 1, 3);
        CashTestDays.seedClosedSession(jdbc, day, "100.00", USER);

        assertThatThrownBy(() -> CashTestDays.seedClosedSession(jdbc, day, "100.00", USER))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CAJ-12: el día de una caja no cambia después de abrir, ni tocando la entidad")
    void dayCannotChangeThroughTheEntity() {
        LocalDate day = LocalDate.of(1905, 1, 4);
        UUID id = CashTestDays.seedClosedSession(jdbc, day, "100.00", USER);

        CashSession session = cashSessionRepository.findById(id).orElseThrow();
        session.setOperationDate(day.plusDays(1));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT operation_date FROM cash_sessions WHERE id = ?", LocalDate.class, id)).isEqualTo(day);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("CAJ-13: si la apertura falla no queda ninguna marca ni caja")
    void failedOpeningLeavesNoMark() {
        // Commits for real, so it needs room after the last session and removes what it created
        requireNoOpenSession();
        LocalDate today = StoreFormat.today();
        LocalDate last = jdbc.queryForObject("SELECT MAX(operation_date) FROM cash_sessions", LocalDate.class);
        LocalDate base = today.minusDays(8);
        assumeTrue(last == null || last.isBefore(base), "La base tiene una caja reciente: se omite");
        BigDecimal balance = jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE is_cash_account = TRUE", BigDecimal.class);
        UUID seeded = CashTestDays.seedClosedSession(jdbc, base, "100.00", USER);
        try {
            List<LocalDate> missing = cashSessionService.getOpeningProposal().unresolvedDays();
            assertThat(missing).isNotEmpty();
            // The opening adjustment comes after the marks and fails without its category
            doReturn(java.util.Optional.empty()).when(financialCategoryRepository).findBySystemKeyAndDeletedAtIsNull(any());

            assertThatThrownBy(() -> cashSessionService.openSession(new OpenCashRequest(new BigDecimal("90.00"), "Monto distinto",
                    today, missing, "Feriado"), USER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ajuste");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_no_attention_days WHERE marked_by = ?", Integer.class, USER)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE operation_date = ?", Integer.class, today)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class)).isZero();
        } finally {
            reset(financialCategoryRepository);
            jdbc.update("DELETE FROM cash_sessions WHERE id = ?", seeded);
            jdbc.update("UPDATE financial_accounts SET current_balance = ? WHERE is_cash_account = TRUE", balance);
        }
    }
}
