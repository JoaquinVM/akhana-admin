package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, GlobalExceptionHandler.class})
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/login - Retorna 200 OK con datos de usuario cuando las credenciales son válidas")
    void login_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginResponse response = new LoginResponse(userId, "admin", Role.ADMIN, "Authentication successful");

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

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
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.message").value("Authentication successful"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Retorna 401 Unauthorized cuando las credenciales son incorrectas")
    void login_Unauthorized_InvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
            .thenThrow(new AuthenticationFailedException("Invalid username or password"));

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
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
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
}
