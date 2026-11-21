package com.akhana.akhana_admin.config;

import com.akhana.akhana_admin.service.impl.StoreFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The store's clock, in America/La_Paz: every service that decides "today", "this month" or the day a movement counts
 * for reads it, so a test can fix the instant (store midnight is 04:00Z).
 */
@Configuration
public class StoreClockConfig {

    @Bean
    public Clock storeClock() {
        return Clock.system(StoreFormat.STORE_ZONE);
    }
}
