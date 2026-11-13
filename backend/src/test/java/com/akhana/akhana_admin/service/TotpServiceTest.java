package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.service.impl.TotpServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    // RFC 6238, appendix B: SHA-1 seed and its 8-digit codes; the 6-digit code is the last 6 digits
    private static final byte[] RFC_SEED = "12345678901234567890".getBytes(US_ASCII);

    private final AuthServiceTest.MutableClock clock = new AuthServiceTest.MutableClock();
    private final TotpService service = new TotpServiceImpl(clock);

    @Test
    @DisplayName("Los códigos coinciden con los vectores de prueba del RFC 6238")
    void codesMatchRfcVectors() {
        assertThat(TotpServiceImpl.code(RFC_SEED, 59 / 30)).isEqualTo("287082");
        assertThat(TotpServiceImpl.code(RFC_SEED, 1111111109L / 30)).isEqualTo("081804");
        assertThat(TotpServiceImpl.code(RFC_SEED, 1111111111L / 30)).isEqualTo("050471");
        assertThat(TotpServiceImpl.code(RFC_SEED, 1234567890L / 30)).isEqualTo("005924");
        assertThat(TotpServiceImpl.code(RFC_SEED, 2000000000L / 30)).isEqualTo("279037");
        assertThat(TotpServiceImpl.code(RFC_SEED, 20000000000L / 30)).isEqualTo("353130");
    }

    @Test
    @DisplayName("Base32 sigue el RFC 4648 y vuelve a los mismos bytes")
    void base32RoundTrip() {
        assertThat(TotpServiceImpl.base32("foobar".getBytes(US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(TotpServiceImpl.base32Decode("mzxw6ytboi")).isEqualTo("foobar".getBytes(US_ASCII));
        String secret = service.generateSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(TotpServiceImpl.base32(TotpServiceImpl.base32Decode(secret))).isEqualTo(secret);
        assertThat(service.generateSecret()).isNotEqualTo(secret);
    }

    @Test
    @DisplayName("Acepta el código actual y un paso antes o después, no dos")
    void acceptsOneStepOfDrift() {
        String secret = service.generateSecret();
        long now = clock.instant().getEpochSecond() / 30;

        assertThat(service.matchingStep(secret, codeAt(secret, now), null)).isEqualTo(OptionalLong.of(now));
        assertThat(service.matchingStep(secret, codeAt(secret, now - 1), null)).isEqualTo(OptionalLong.of(now - 1));
        assertThat(service.matchingStep(secret, codeAt(secret, now + 1), null)).isEqualTo(OptionalLong.of(now + 1));
        assertThat(service.matchingStep(secret, codeAt(secret, now - 2), null)).isEmpty();
        assertThat(service.matchingStep(secret, codeAt(secret, now + 2), null)).isEmpty();

        clock.advance(Duration.ofSeconds(30));
        assertThat(service.matchingStep(secret, codeAt(secret, now), null)).isEqualTo(OptionalLong.of(now));
    }

    @Test
    @DisplayName("Un código de un paso ya usado, o anterior, no se acepta")
    void rejectsUsedSteps() {
        String secret = service.generateSecret();
        long now = clock.instant().getEpochSecond() / 30;

        assertThat(service.matchingStep(secret, codeAt(secret, now), now)).isEmpty();
        assertThat(service.matchingStep(secret, codeAt(secret, now - 1), now)).isEmpty();
        assertThat(service.matchingStep(secret, codeAt(secret, now + 1), now)).isEqualTo(OptionalLong.of(now + 1));
    }

    @Test
    @DisplayName("Solo acepta 6 dígitos; los espacios del medio no importan")
    void acceptsOnlySixDigits() {
        String secret = service.generateSecret();
        String code = codeAt(secret, clock.instant().getEpochSecond() / 30);

        assertThat(service.matchingStep(secret, code.substring(0, 3) + " " + code.substring(3), null)).isPresent();
        assertThat(service.matchingStep(secret, code.substring(0, 5), null)).isEmpty();
        assertThat(service.matchingStep(secret, "12345a", null)).isEmpty();
        assertThat(service.matchingStep(secret, null, null)).isEmpty();
        assertThat(service.matchingStep(null, code, null)).isEmpty();
    }

    @Test
    @DisplayName("Con el reloj del RFC, el código del segundo 59 es el del vector")
    void usesTheClock() {
        clock.advance(Duration.between(clock.instant(), Instant.ofEpochSecond(59)));
        String rfcSecret = TotpServiceImpl.base32(RFC_SEED);

        assertThat(service.matchingStep(rfcSecret, "287082", null)).isEqualTo(OptionalLong.of(1));
    }

    private static String codeAt(String secret, long step) {
        return TotpServiceImpl.code(TotpServiceImpl.base32Decode(secret), step);
    }
}
