package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.exception.LoginBlockedException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.AuthService;
import com.akhana.akhana_admin.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/auth/login - Retorna 200 OK con token y datos de usuario cuando las credenciales son válidas")
    void login_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9.fake.jwt.token";
        LoginResponse response = new LoginResponse(fakeToken, userId, "admin", Role.ADMINISTRATOR, "Sesión iniciada.");

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(response);

        String jsonPayload = """
            {
                "username": "admin",
                "password": "12345admin"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(fakeToken))
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMINISTRATOR"))
            .andExpect(jsonPath("$.message").value("Sesión iniciada."));
    }

    @Test
    @DisplayName("POST /api/auth/login - Retorna 429 con mensaje en español cuando el login está bloqueado")
    void login_TooManyRequests_WhenBlocked() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
            .thenThrow(new LoginBlockedException("Demasiados intentos fallidos. Espere 15 minutos e intente nuevamente."));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"admin\", \"password\": \"x\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("Too Many Requests"))
            .andExpect(jsonPath("$.message").value("Demasiados intentos fallidos. Espere 15 minutos e intente nuevamente."));
    }

    @Test
    @DisplayName("POST /api/auth/login - Usa la IP de la conexión e ignora X-Forwarded-For sin proxy configurado")
    void login_UsesRemoteAddressNotForwardedHeader() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
            .thenReturn(new LoginResponse("t", UUID.randomUUID(), "admin", Role.ADMINISTRATOR, "Sesión iniciada."));

        mockMvc.perform(post("/api/auth/login")
                .with(request -> { request.setRemoteAddr("203.0.113.10"); return request; })
                .header("X-Forwarded-For", "1.2.3.4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"admin\", \"password\": \"x\"}"))
            .andExpect(status().isOk());

        verify(authService).login(any(LoginRequest.class), eq("203.0.113.10"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Retorna 401 Unauthorized cuando las credenciales son incorrectas")
    void login_Unauthorized_InvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
            .thenThrow(new AuthenticationFailedException("Usuario o contraseña incorrectos."));

        String jsonPayload = """
            {
                "username": "admin",
                "password": "wrongPassword"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos."));
    }

    @Test
    @DisplayName("POST /api/auth/login - Retorna 400 Bad Request cuando el username o password están en blanco")
    void login_BadRequest_BlankFields() throws Exception {
        String jsonPayload = """
            {
                "username": "",
                "password": ""
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("GET /api/auth/me - Retorna 200 OK con los datos del usuario autenticado vía JWT")
    void getCurrentUser_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .username("admin")
            .role(Role.ADMINISTRATOR)
            .active(true)
            .build();

        String token = "valid.test.token";
        when(jwtService.extractUsername(token)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(token, "admin")).thenReturn(true);

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMINISTRATOR"));
    }

    @Test
    @DisplayName("GET /api/auth/me - Retorna 401 Unauthorized cuando no se envía el header Authorization")
    void getCurrentUser_Unauthorized_MissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("La sesión no es válida o venció; vuelva a iniciar sesión."));
    }

    @Test
    @DisplayName("GET /api/auth/me - Retorna 401 Unauthorized cuando el token es inválido")
    void getCurrentUser_Unauthorized_InvalidToken() throws Exception {
        String invalidToken = "invalid.test.token";
        when(jwtService.extractUsername(invalidToken)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("La sesión no es válida o venció; vuelva a iniciar sesión."));
    }

    private User authenticate(String token, boolean mustChangePassword) {
        User user = User.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .role(Role.ADMINISTRATOR)
            .active(true)
            .mustChangePassword(mustChangePassword)
            .build();
        when(jwtService.extractUsername(token)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(token, "admin")).thenReturn(true);
        return user;
    }

    @Test
    @DisplayName("POST /api/auth/change-password - Retorna 200 con el token nuevo y delega en el servicio con el usuario autenticado")
    void changePassword_Success() throws Exception {
        User user = authenticate("valid.token", true);
        when(authService.changePassword(eq(user.getId()), any(ChangePasswordRequest.class))).thenReturn("new.token");

        mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "currentPassword": "initial-pass", "newPassword": "new-strong-pass" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("new.token"));

        verify(authService).changePassword(eq(user.getId()), any(ChangePasswordRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/change-password - Retorna 400 si la nueva contraseña es demasiado corta")
    void changePassword_TooShort_BadRequest() throws Exception {
        authenticate("valid.token", true);

        mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "currentPassword": "initial-pass", "newPassword": "short" }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La nueva contraseña debe tener entre 8 y 100 caracteres."));

        verify(authService, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("POST /api/auth/change-password - Retorna 401 sin token")
    void changePassword_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "currentPassword": "initial-pass", "newPassword": "new-strong-pass" }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me - Informa mustChangePassword y sigue accesible mientras la contraseña debe cambiarse")
    void getCurrentUser_MustChangePassword() throws Exception {
        authenticate("valid.token", true);

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword").value(true));
    }
}
