package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.service.impl.CashDaySequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** CAJ-13 with the example of the spec: the last session is of Friday 30/10 and today is Tuesday 3/11. */
class CashDaySequenceTest {

    private static final LocalDate FRI_30 = LocalDate.of(2026, 10, 30);
    private static final LocalDate SAT_31 = LocalDate.of(2026, 10, 31);
    private static final LocalDate SUN_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate MON_2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUE_3 = LocalDate.of(2026, 11, 3);

    @Test
    @DisplayName("Elegir el 31/10: no queda ningún día atrás sin resolver")
    void nextDay_missesNothing() {
        assertThat(CashDaySequence.missingDays(FRI_30, SAT_31, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("Elegir el 3/11: faltan el sábado y el lunes; el domingo no cuenta")
    void tuesday_missesSaturdayAndMonday() {
        assertThat(CashDaySequence.missingDays(FRI_30, TUE_3, Set.of())).isEqualTo(List.of(SAT_31, MON_2));
    }

    @Test
    @DisplayName("Elegir el domingo: el sábado que falta se resuelve igual")
    void sunday_missesTheSaturday() {
        assertThat(CashDaySequence.missingDays(FRI_30, SUN_1, Set.of())).isEqualTo(List.of(SAT_31));
    }

    @Test
    @DisplayName("El lunes se abre igual si el domingo tuvo caja o no la tuvo")
    void monday_afterSundayWithOrWithoutSession() {
        assertThat(CashDaySequence.missingDays(SAT_31, MON_2, Set.of())).isEmpty();
        assertThat(CashDaySequence.missingDays(SUN_1, MON_2, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("Un día marcado ya no falta")
    void markedDay_isNotMissing() {
        assertThat(CashDaySequence.missingDays(FRI_30, TUE_3, Set.of(SAT_31))).isEqualTo(List.of(MON_2));
    }
}
