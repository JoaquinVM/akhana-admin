package com.akhana.akhana_admin.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** CAJ-13: sessions go in sequence. Sundays do not count; any other day needs a session or the mark «sin atención». */
public final class CashDaySequence {

    private CashDaySequence() {
    }

    /** The days after {@code lastDay} and before {@code day} that are neither Sundays nor marked, in order. */
    public static List<LocalDate> missingDays(LocalDate lastDay, LocalDate day, Set<LocalDate> marked) {
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate date = lastDay.plusDays(1); date.isBefore(day); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY && !marked.contains(date)) {
                missing.add(date);
            }
        }
        return missing;
    }
}
