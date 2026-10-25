package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.exception.LoginBlockedException;
import com.akhana.akhana_admin.service.impl.AuthServiceImpl;
import com.akhana.akhana_admin.service.impl.LoginAttemptServiceImpl;
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

    @BeforeEach
    void setUp() {
        LoginAttemptService loginAttemptService = new LoginAttemptServiceImpl(
            5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, loginAttemptService);
        activeUser = User.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .password("$2a$10$hashedPassword")
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
        assertThat(response.role()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(response.message()).isEqualTo("Authentication successful");

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
            .hasMessage("Invalid username or password");

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
            .hasMessage("Invalid username or password");

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
            .hasMessage("Invalid username or password");

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
        assertThat(response.message()).isEqualTo("Password change required");
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
            .hasMessage("La contraseña actual no es correcta");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cambiar la contraseña falla si la nueva es igual a la actual")
    void changePassword_SameAsCurrent_Fails() {
        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("same-password", "$2a$10$hashedPassword")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(activeUser.getId(), new ChangePasswordRequest("same-password", "same-password")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La nueva contraseña debe ser distinta de la actual");
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
            .hasMessage("La nueva contraseña no puede ser igual al nombre de usuario");
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
            .hasMessage("Invalid username or password");
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

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "x"), "198.51.100.7"))
            .isInstanceOf(LoginBlockedException.class);
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
