package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.exception.LoginBlockedException;
import com.akhana.akhana_admin.service.impl.LoginAttemptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.10";

    private final AuthServiceTest.MutableClock clock = new AuthServiceTest.MutableClock();
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptServiceImpl(3, 10, Duration.ofMinutes(15), Duration.ofMinutes(30), clock);
    }

    @Test
    @DisplayName("Los fallos fuera de la ventana no se acumulan")
    void failuresOutsideWindowAreForgotten() {
        service.recordFailure("ana", IP);
        service.recordFailure("ana", IP);
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> service.recordFailure("ana", IP)).doesNotThrowAnyException();
        assertThatCode(() -> service.checkNotBlocked("ana", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El bloqueo dura lo configurado y el mensaje lo indica")
    void lockLastsConfiguredLockout() {
        service.recordFailure("ana", IP);
        service.recordFailure("ana", IP);
        assertThatThrownBy(() -> service.recordFailure("ana", IP))
            .isInstanceOf(LoginBlockedException.class)
            .hasMessage("Demasiados intentos fallidos. Espere 30 minutos e intente nuevamente.");

        clock.advance(Duration.ofMinutes(29));
        assertThatThrownBy(() -> service.checkNotBlocked("ana", "198.51.100.7"))
            .isInstanceOf(LoginBlockedException.class);

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> service.checkNotBlocked("ana", IP)).doesNotThrowAnyException();
        assertThatCode(() -> service.recordFailure("ana", IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El éxito reinicia el usuario pero no la IP")
    void successResetsUserOnly() {
        for (int i = 0; i < 9; i++) {
            try {
                service.recordFailure("user" + i, IP);
            } catch (LoginBlockedException ignored) {
                // expected for nothing below the limits
            }
        }
        service.recordSuccess("user0");

        assertThatThrownBy(() -> service.recordFailure("other", IP))
            .isInstanceOf(LoginBlockedException.class);
    }
}
