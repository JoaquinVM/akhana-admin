package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.service.impl.StoreFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StoreFormatTest {

    @Test
    @DisplayName("Los montos de los mensajes se leen como en la pantalla: Bs 1.234,50, 2 decimales HALF_UP")
    void money() {
        assertThat(StoreFormat.money(new BigDecimal("1234.5"))).isEqualTo("Bs 1.234,50");
        assertThat(StoreFormat.money(new BigDecimal("0"))).isEqualTo("Bs 0,00");
        assertThat(StoreFormat.money(new BigDecimal("999999.995"))).isEqualTo("Bs 1.000.000,00");
        assertThat(StoreFormat.money(new BigDecimal("-50"))).isEqualTo("Bs -50,00");
        assertThat(StoreFormat.money(new BigDecimal("0.005"))).isEqualTo("Bs 0,01");
    }

    @Test
    @DisplayName("Los kilos llevan coma decimal y la escala pedida")
    void number() {
        assertThat(StoreFormat.number(new BigDecimal("0.454"), 3)).isEqualTo("0,454");
        assertThat(StoreFormat.number(new BigDecimal("1500"), 3)).isEqualTo("1.500,000");
    }

    @Test
    @DisplayName("CAJ-11: el día de tienda de un instante es el de La Paz (UTC-4)")
    void storeDay() {
        // 09:10 of 31-10 in La Paz
        assertThat(StoreFormat.storeDay(Instant.parse("2026-10-31T13:10:00Z"))).isEqualTo(LocalDate.of(2026, 10, 31));
        // 23:59 of 31-10 in La Paz is already 01-11 in UTC
        assertThat(StoreFormat.storeDay(Instant.parse("2026-11-01T03:59:00Z"))).isEqualTo(LocalDate.of(2026, 10, 31));
        // 00:20 of 01-11 in La Paz
        assertThat(StoreFormat.storeDay(Instant.parse("2026-11-01T04:20:00Z"))).isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
