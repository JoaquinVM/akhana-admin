package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.dto.TwoFactorLoginRequest;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.exception.LoginBlockedException;
import com.akhana.akhana_admin.service.impl.AuthServiceImpl;
import com.akhana.akhana_admin.service.impl.LoginAttemptServiceImpl;
import com.akhana.akhana_admin.service.impl.TotpServiceImpl;
import com.akhana.akhana_admin.service.impl.TwoFactorChallengeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private static final String IP = "203.0.113.10";

    private final MutableClock clock = new MutableClock();
    private AuthServiceImpl authService;

    private User activeUser;
    private User administrator;

    @BeforeEach
    void setUp() {
        LoginAttemptService loginAttemptService = new LoginAttemptServiceImpl(
            5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, loginAttemptService,
            new TotpServiceImpl(clock), new TwoFactorChallengeServiceImpl(clock));
        // An operator: the password alone opens the session; administrators are covered below (USR-08)
        activeUser = User.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .password("$2a$10$hashedPassword")
            .role(Role.OPERATOR)
            .active(true)
            .build();
        administrator = User.builder()
            .id(UUID.randomUUID())
            .username("duena")
            .password("$2a$10$adminHash")
            .role(Role.ADMINISTRATOR)
            .active(true)
            .build();
    }

    @Test
    @DisplayName("Debe autenticar exitosamente cuando las credenciales son válidas y generar el JWT")
    void login_Success() {
        LoginRequest request = new LoginRequest("admin", "12345admin");
        String expectedToken = "mocked.jwt.token";

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("12345admin", activeUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn(expectedToken);

        LoginResponse response = authService.login(request, IP);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo(expectedToken);
        assertThat(response.id()).isEqualTo(activeUser.getId());
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo(Role.OPERATOR);
        assertThat(response.message()).isEqualTo("Sesión iniciada.");
        assertThat(response.twoFactor()).isNull();

        verify(userRepository, times(1)).findByUsername("admin");
        verify(passwordEncoder, times(1)).matches("12345admin", activeUser.getPassword());
        verify(jwtService, times(1)).generateToken(activeUser);
    }

    @Test
    @DisplayName("Debe fallar con HTTP 401 genérico si el usuario no existe y no generar JWT")
    void login_UserNotFound() {
        LoginRequest request = new LoginRequest("nonexistent", "secret");

        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, IP))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Usuario o contraseña incorrectos.");

        verify(userRepository, times(1)).findByUsername("nonexistent");
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Debe fallar con HTTP 401 genérico si la contraseña no coincide y no generar JWT")
    void login_InvalidPassword() {
        LoginRequest request = new LoginRequest("admin", "wrongPassword");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongPassword", activeUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, IP))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Usuario o contraseña incorrectos.");

        verify(userRepository, times(1)).findByUsername("admin");
        verify(passwordEncoder, times(1)).matches("wrongPassword", activeUser.getPassword());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Debe fallar con HTTP 401 genérico si el usuario está inactivo y no generar JWT")
    void login_InactiveUser() {
        User inactiveUser = User.builder()
            .id(UUID.randomUUID())
            .username("seller")
            .password("$2a$10$hashedPassword")
            .role(Role.OPERATOR)
            .active(false)
            .build();

        LoginRequest request = new LoginRequest("seller", "12345seller");

        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authService.login(request, IP))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Usuario o contraseña incorrectos.");

        verify(userRepository, times(1)).findByUsername("seller");
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("El login informa que el usuario debe cambiar la contraseña")
    void login_MustChangePassword_IsReported() {
        activeUser.setMustChangePassword(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("initial-pass", activeUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("token");

        LoginResponse response = authService.login(new LoginRequest("admin", "initial-pass"), IP);

        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.message()).isEqualTo("Debe cambiar su contraseña.");
    }

    @Test
    @DisplayName("Cambiar la contraseña guarda el nuevo hash y quita la marca de cambio obligatorio")
    void changePassword_Success() {
        activeUser.setMustChangePassword(true);
        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("initial-pass", "$2a$10$hashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("new-strong-pass")).thenReturn("$2a$10$newHash");

        authService.changePassword(activeUser.getId(), new ChangePasswordRequest("initial-pass", "new-strong-pass"));

        assertThat(activeUser.getPassword()).isEqualTo("$2a$10$newHash");
        assertThat(activeUser.isMustChangePassword()).isFalse();
        assertThat(activeUser.getUpdatedBy()).isEqualTo("admin");
        verify(userRepository).save(activeUser);
    }

    @Test
    @DisplayName("Cambiar la contraseña falla si la actual no coincide")
    void changePassword_WrongCurrentPassword_Fails() {
        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(activeUser.getId(), new ChangePasswordRequest("wrong", "new-strong-pass")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La contraseña actual no es correcta.");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cambiar la contraseña falla si la nueva es igual a la actual")
    void changePassword_SameAsCurrent_Fails() {
        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("same-password", "$2a$10$hashedPassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(activeUser.getId(), new ChangePasswordRequest("same-password", "same-password")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La nueva contraseña debe ser distinta de la actual.");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cambiar la contraseña falla si la nueva es igual al nombre de usuario")
    void changePassword_SameAsUsername_Fails() {
        activeUser.setUsername("administrador");
        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("initial-pass", "$2a$10$hashedPassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(activeUser.getId(), new ChangePasswordRequest("initial-pass", "Administrador")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La contraseña no puede ser igual al nombre de usuario.");
        verify(userRepository, never()).save(any());
    }
    private static final String BLOCKED_MESSAGE = "Demasiados intentos fallidos. Espere 15 minutos e intente nuevamente.";

    private void failLogins(String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            try {
                authService.login(new LoginRequest(username, "wrong"), ip);
            } catch (AuthenticationFailedException | LoginBlockedException ignored) {
                // expected
            }
        }
    }

    @Test
    @DisplayName("Tras 5 fallos el usuario queda bloqueado, aunque luego envíe la contraseña correcta")
    void login_LocksUserAfterMaxFailures() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", activeUser.getPassword())).thenReturn(false);

        failLogins("admin", IP, 4);
        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong"), IP))
            .isInstanceOf(LoginBlockedException.class)
            .hasMessage(BLOCKED_MESSAGE);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "12345admin"), IP))
            .isInstanceOf(LoginBlockedException.class)
            .hasMessage(BLOCKED_MESSAGE);
        verify(passwordEncoder, never()).matches("12345admin", activeUser.getPassword());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Al pasar el bloqueo el usuario puede volver a ingresar")
    void login_UnlocksAfterLockout() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", activeUser.getPassword())).thenReturn(false);
        when(passwordEncoder.matches("12345admin", activeUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("token");

        failLogins("admin", IP, 5);
        clock.advance(Duration.ofMinutes(15));

        assertThat(authService.login(new LoginRequest("admin", "12345admin"), IP).token()).isEqualTo("token");
    }

    @Test
    @DisplayName("Un login exitoso reinicia el contador del usuario")
    void login_SuccessResetsUserCounter() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", activeUser.getPassword())).thenReturn(false);
        when(passwordEncoder.matches("12345admin", activeUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("token");

        failLogins("admin", IP, 4);
        authService.login(new LoginRequest("admin", "12345admin"), IP);
        failLogins("admin", IP, 3);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong"), IP))
            .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("La misma IP queda bloqueada tras 20 fallos con usuarios distintos")
    void login_LocksIpAcrossDifferentUsers() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("12345admin", activeUser.getPassword())).thenReturn(true);

        for (int i = 0; i < 20; i++) {
            failLogins("user" + i, IP, 1);
        }

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "12345admin"), IP))
            .isInstanceOf(LoginBlockedException.class)
            .hasMessage(BLOCKED_MESSAGE);
        when(jwtService.generateToken(activeUser)).thenReturn("token");
        assertThat(authService.login(new LoginRequest("admin", "12345admin"), "198.51.100.7").token())
            .isEqualTo("token");
    }

    @Test
    @DisplayName("Un usuario inexistente recibe los mismos mensajes que uno existente")
    void login_NonexistentUserGetsSameMessages() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("wrong", activeUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "wrong"), IP))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Usuario o contraseña incorrectos.");
        failLogins("ghost", IP, 3);
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "wrong"), IP))
            .isInstanceOf(LoginBlockedException.class)
            .hasMessage(BLOCKED_MESSAGE);

        failLogins("admin", "198.51.100.7", 4);
        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong"), "198.51.100.7"))
            .isInstanceOf(LoginBlockedException.class)
            .hasMessage(BLOCKED_MESSAGE);
    }

    @Test
    @DisplayName("El bloqueo no distingue mayúsculas ni espacios en el usuario")
    void login_LockIgnoresUsernameCase() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        failLogins("Admin", IP, 2);
        failLogins(" ADMIN ", IP, 3);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "x"), IP))
            .isInstanceOf(LoginBlockedException.class);
    }

    // --- Second factor of administrators (USR-08) ---

    private LoginResponse adminPasswordStep(String ip) {
        when(userRepository.findByUsername("duena")).thenReturn(Optional.of(administrator));
        when(passwordEncoder.matches("clave-duena", administrator.getPassword())).thenReturn(true);
        return authService.login(new LoginRequest("duena", "clave-duena"), ip);
    }

    private String codeNow(String secret, int stepsFromNow) {
        long step = Math.floorDiv(clock.instant().getEpochSecond(), 30) + stepsFromNow;
        return TotpServiceImpl.code(TotpServiceImpl.base32Decode(secret), step);
    }

    private LoginResponse secondFactor(LoginResponse challenge, String code) {
        return authService.loginWithSecondFactor(new TwoFactorLoginRequest(challenge.twoFactor().challengeId(), code), IP);
    }

    @Test
    @DisplayName("Con la contraseña sola, un administrador sin 2FA recibe el QR para activarlo y ningún token")
    void administrator_WithoutSecondFactor_GetsActivation() {
        LoginResponse response = adminPasswordStep(IP);

        assertThat(response.token()).isNull();
        assertThat(response.id()).isNull();
        assertThat(response.permissions()).isEmpty();
        assertThat(response.twoFactor().setupRequired()).isTrue();
        assertThat(response.twoFactor().secret()).matches("[A-Z2-7]{32}");
        assertThat(response.twoFactor().otpauthUri()).isEqualTo("otpauth://totp/Akhana%20Admin:duena?secret="
            + response.twoFactor().secret() + "&issuer=Akhana%20Admin&algorithm=SHA1&digits=6&period=30");
        assertThat(administrator.getTotpSecret()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("El 2FA queda activo solo al confirmar un código válido, y entonces se entrega la sesión")
    void administrator_ActivatesWithValidCodeOnly() {
        when(userRepository.findById(administrator.getId())).thenReturn(Optional.of(administrator));
        when(jwtService.generateToken(administrator)).thenReturn("admin-token");
        LoginResponse challenge = adminPasswordStep(IP);
        String secret = challenge.twoFactor().secret();

        assertThatThrownBy(() -> secondFactor(challenge, "000000"))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Código incorrecto.");
        assertThat(administrator.getTotpSecret()).isNull();
        verify(userRepository, never()).save(any());

        LoginResponse session = secondFactor(challenge, codeNow(secret, 0));

        assertThat(session.token()).isEqualTo("admin-token");
        assertThat(session.role()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(administrator.getTotpSecret()).isEqualTo(secret);
        assertThat(administrator.getTotpEnabledAt()).isNotNull();
        assertThat(administrator.getTotpLastStep()).isEqualTo(Math.floorDiv(clock.instant().getEpochSecond(), 30));
        verify(userRepository).save(administrator);
    }

    @Test
    @DisplayName("Con el 2FA activo se pide solo el código; uno ya usado no vuelve a servir")
    void administrator_WithSecondFactor_EntersWithCodeOnce() {
        String secret = new TotpServiceImpl(clock).generateSecret();
        administrator.setTotpSecret(secret);
        when(userRepository.findById(administrator.getId())).thenReturn(Optional.of(administrator));
        when(jwtService.generateToken(administrator)).thenReturn("admin-token");

        LoginResponse challenge = adminPasswordStep(IP);
        assertThat(challenge.twoFactor().setupRequired()).isFalse();
        assertThat(challenge.twoFactor().secret()).isNull();
        assertThat(challenge.twoFactor().otpauthUri()).isNull();

        String code = codeNow(secret, 0);
        assertThat(secondFactor(challenge, code).token()).isEqualTo("admin-token");

        LoginResponse again = adminPasswordStep(IP);
        assertThatThrownBy(() -> secondFactor(again, code))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Código incorrecto.");
        // The previous step is older than the one just used: it does not serve either
        assertThatThrownBy(() -> secondFactor(again, codeNow(secret, -1)))
            .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("El desafío vence a los 5 minutos y se cierra tras 5 códigos incorrectos")
    void administrator_ChallengeExpiresAndEndsAfterFiveFailures() {
        String secret = new TotpServiceImpl(clock).generateSecret();
        administrator.setTotpSecret(secret);
        when(userRepository.findById(administrator.getId())).thenReturn(Optional.of(administrator));

        LoginResponse expiring = adminPasswordStep(IP);
        clock.advance(Duration.ofMinutes(5));
        assertThatThrownBy(() -> secondFactor(expiring, codeNow(secret, 0)))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("El ingreso venció. Vuelva a ingresar su usuario y contraseña.");

        LoginResponse challenge = adminPasswordStep("198.51.100.7");
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.loginWithSecondFactor(
                    new TwoFactorLoginRequest(challenge.twoFactor().challengeId(), "000000"), "198.51.100.7"))
                .isInstanceOf(AuthenticationFailedException.class);
        }
        assertThatThrownBy(() -> authService.loginWithSecondFactor(
                new TwoFactorLoginRequest(challenge.twoFactor().challengeId(), "000000"), "198.51.100.7"))
            .isInstanceOf(LoginBlockedException.class);
        clock.advance(Duration.ofMinutes(15));
        assertThatThrownBy(() -> authService.loginWithSecondFactor(
                new TwoFactorLoginRequest(challenge.twoFactor().challengeId(), codeNow(secret, 0)), "198.51.100.7"))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("El ingreso venció. Vuelva a ingresar su usuario y contraseña.");
    }

    @Test
    @DisplayName("Los códigos incorrectos cuentan como fallos del login y bloquean a ese usuario desde esa IP")
    void administrator_WrongCodesCountAsFailedLogins() {
        String secret = new TotpServiceImpl(clock).generateSecret();
        administrator.setTotpSecret(secret);
        when(userRepository.findById(administrator.getId())).thenReturn(Optional.of(administrator));

        for (int i = 0; i < 4; i++) {
            LoginResponse challenge = adminPasswordStep(IP);
            assertThatThrownBy(() -> secondFactor(challenge, "000000")).isInstanceOf(AuthenticationFailedException.class);
        }
        LoginResponse last = adminPasswordStep(IP);
        assertThatThrownBy(() -> secondFactor(last, "000000")).isInstanceOf(LoginBlockedException.class);

        assertThatThrownBy(() -> adminPasswordStep(IP)).isInstanceOf(LoginBlockedException.class);
        assertThat(adminPasswordStep("198.51.100.7").twoFactor()).isNotNull();
    }

    @Test
    @DisplayName("Si quitaron el 2FA mientras el desafío estaba abierto, hay que volver a ingresar")
    void administrator_SecondFactorRemovedMeanwhile_EndsChallenge() {
        String secret = new TotpServiceImpl(clock).generateSecret();
        administrator.setTotpSecret(secret);
        when(userRepository.findById(administrator.getId())).thenReturn(Optional.of(administrator));
        LoginResponse challenge = adminPasswordStep(IP);

        administrator.setTotpSecret(null);

        assertThatThrownBy(() -> secondFactor(challenge, codeNow(secret, 0)))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("El ingreso venció. Vuelva a ingresar su usuario y contraseña.");
        verifyNoInteractions(jwtService);
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-25T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
