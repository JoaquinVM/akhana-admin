package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.CreateUserRequest;
import com.akhana.akhana_admin.dto.UpdateUserRequest;
import com.akhana.akhana_admin.dto.UserResponse;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class UserControllerTest {

    private static final String ADMIN_TOKEN = "valid.admin.jwt";
    private static final String OPERATOR_TOKEN = "valid.operator.jwt";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    private User adminUser;
    private User operatorUser;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .fullName("Administrador")
                .role(Role.ADMINISTRATOR)
                .active(true)
                .build();

        operatorUser = User.builder()
                .id(UUID.randomUUID())
                .username("operator")
                .fullName("Operador")
                .role(Role.OPERATOR)
                .active(true)
                .build();

        when(jwtService.extractUsername(ADMIN_TOKEN)).thenReturn("admin");
        when(jwtService.isTokenValid(ADMIN_TOKEN, "admin")).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        when(jwtService.extractUsername(OPERATOR_TOKEN)).thenReturn("operator");
        when(jwtService.isTokenValid(OPERATOR_TOKEN, "operator")).thenReturn(true);
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(operatorUser));
    }

    @Test
    @DisplayName("GET /api/users con usuario administrador retorna 200 OK")
    void getUsers_asAdmin_returnsOk() throws Exception {
        UserResponse response = new UserResponse(
                sampleUserId, "User Name", "username", Role.ADMINISTRATOR, true,
                Role.ADMINISTRATOR.permissionKeys(),
                Instant.now(), "system", null, null, null, null
        );
        when(userService.getUsers(any(), any())).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("username"))
                .andExpect(jsonPath("$.content[0].fullName").value("User Name"));
    }

    @Test
    @DisplayName("GET /api/users con operador retorna 403 Forbidden con mensaje")
    void getUsers_asOperator_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OPERATOR_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tiene permiso para realizar esta acción."));
    }

    @Test
    @DisplayName("POST /api/users con datos válidos retorna 201 Created")
    void createUser_validRequest_returnsCreated() throws Exception {
        UserResponse response = new UserResponse(
                sampleUserId, "Carlos Perez", "cperez", Role.OPERATOR, true,
                Role.OPERATOR.permissionKeys(),
                Instant.now(), "admin", null, null, null, null
        );

        when(userService.createUser(any(CreateUserRequest.class), eq("admin"))).thenReturn(response);

        String json = """
                {
                    "fullName": "Carlos Perez",
                    "username": "cperez",
                    "password": "password123",
                    "role": "OPERATOR"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cperez"))
                .andExpect(jsonPath("$.fullName").value("Carlos Perez"));
    }

    @Test
    @DisplayName("PUT /api/users/{id} con datos válidos retorna 200 OK")
    void updateUser_validRequest_returnsOk() throws Exception {
        UserResponse response = new UserResponse(
                sampleUserId, "Carlos Perez Modificado", "cperez", Role.OPERATOR, true,
                Role.OPERATOR.permissionKeys(),
                Instant.now().minusSeconds(100), "admin", Instant.now(), "admin", null, null
        );

        when(userService.updateUser(eq(sampleUserId), any(UpdateUserRequest.class), eq("admin"))).thenReturn(response);

        String json = """
                {
                    "fullName": "Carlos Perez Modificado",
                    "role": "OPERATOR"
                }
                """;

        mockMvc.perform(put("/api/users/" + sampleUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Carlos Perez Modificado"));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} retorna 204 No Content")
    void deleteUser_success_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/" + sampleUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/users/roles retorna los permisos por acción de cada rol")
    void getRoles_returnsPermissionsPerRole() throws Exception {
        mockMvc.perform(get("/api/users/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ADMINISTRATOR.length()").value(Role.allPermissionKeys().size()))
                .andExpect(jsonPath("$.OPERATOR", org.hamcrest.Matchers.hasItem("POS")))
                .andExpect(jsonPath("$.OPERATOR", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("USERS"))));
    }

    @Test
    @DisplayName("POST /api/users sin rol retorna 400")
    void createUser_withoutRole_returnsBadRequest() throws Exception {
        String json = """
                {
                    "fullName": "Carlos Perez",
                    "username": "cperez",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
