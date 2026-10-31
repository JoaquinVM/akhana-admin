package com.akhana.akhana_admin.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Store-wide conventions: the store's time zone (store days, America/La_Paz) and how amounts read in messages,
 * es-BO style ("Bs 1.234,50"), the same as the screens.
 */
public final class StoreFormat {

    public static final ZoneId STORE_ZONE = ZoneId.of("America/La_Paz");

    private StoreFormat() {
    }

    /** The store day (America/La_Paz) of an instant: the day of operation of what is not tied to a session (CAJ-11). */
    public static LocalDate storeDay(Instant instant) {
        return instant.atZone(STORE_ZONE).toLocalDate();
    }

    /** Today in the store. */
    public static LocalDate today() {
        return LocalDate.now(STORE_ZONE);
    }

    /** An amount for a message: "Bs 1.234,50", 2 decimals HALF_UP. */
    public static String money(BigDecimal amount) {
        return "Bs " + number(amount, 2);
    }

    /** A quantity or weight for a message: "0,454", the given decimals. */
    public static String number(BigDecimal value, int decimals) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        symbols.setMinusSign('-');
        // DecimalFormat is not thread-safe, so each call builds its own
        DecimalFormat format = new DecimalFormat("#,##0." + "0".repeat(decimals), symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value.setScale(decimals, RoundingMode.HALF_UP));
    }
}
