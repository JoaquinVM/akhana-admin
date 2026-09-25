package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .password("$2a$10$hashedPassword")
            .role(Role.ADMIN)
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

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo(expectedToken);
        assertThat(response.id()).isEqualTo(activeUser.getId());
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
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

        assertThatThrownBy(() -> authService.login(request))
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

        assertThatThrownBy(() -> authService.login(request))
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
            .role(Role.SELLER)
            .active(false)
            .build();

        LoginRequest request = new LoginRequest("seller", "12345seller");

        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Invalid username or password");

        verify(userRepository, times(1)).findByUsername("seller");
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtService);
    }
}
