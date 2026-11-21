package com.akhana.akhana_admin.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Fixed clocks for the store's day boundaries. La Paz is UTC-4 all year: 23:59:59 of 30/11 there is 03:59:59Z of
 * 01/12 and the store's midnight is 04:00Z. The clocks carry UTC on purpose, so a service that read the day in the
 * clock's zone instead of the store's would fail.
 */
public final class StoreClocks {

    /** 30/11/2026 23:59:59 in La Paz: still November and the 30th. */
    public static final Instant LAST_SECOND_OF_NOVEMBER = Instant.parse("2026-12-01T03:59:59Z");
    /** 01/12/2026 00:00:00 in La Paz: the first day of December. */
    public static final Instant FIRST_SECOND_OF_DECEMBER = Instant.parse("2026-12-01T04:00:00Z");

    private StoreClocks() {
    }

    public static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    public static Clock at(String instant) {
        return at(Instant.parse(instant));
    }
}
