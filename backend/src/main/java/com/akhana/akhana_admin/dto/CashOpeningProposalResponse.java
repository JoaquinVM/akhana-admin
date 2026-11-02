package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashOpeningProposalResponse(
    // CAJ-10: proposed opening amount, taken from the last closed session (the Caja balance and no number when there is none, FIN-01)
    BigDecimal proposedAmount,
    Long previousSessionNumber,
    // CAJ-12: today in the store and the first day that can be chosen (the day after the last session, historical
    // ones included; null without sessions)
    LocalDate today,
    LocalDate minDay,
    Long lastSessionNumber,
    LocalDate lastSessionDay,
    // CAJ-12: the number of the last session when it is today's and already closed; else null
    Long todayClosedSessionNumber,
    // CAJ-13: days from minDay up to yesterday without a session or mark, Sundays left out; opening a day requires
    // confirming those before it
    List<LocalDate> unresolvedDays
) {}
