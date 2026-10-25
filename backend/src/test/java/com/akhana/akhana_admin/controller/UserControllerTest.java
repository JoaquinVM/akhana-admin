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
import com.akhana.akhana_admin.model.UserPermission;
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
import java.util.Set;
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
    private static final String SELLER_TOKEN = "valid.seller.jwt";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    private User adminUser;
    private User sellerUser;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .fullName("Administrador")
                .role(Role.ADMIN)
                .active(true)
                .permissions(Set.of(UserPermission.SECURITY_USERS.name()))
                .build();

        sellerUser = User.builder()
                .id(UUID.randomUUID())
                .username("seller")
                .fullName("Vendedor")
                .role(Role.SELLER)
                .active(true)
                .permissions(Set.of(UserPermission.SALES_POS.name()))
                .build();

        when(jwtService.extractUsername(ADMIN_TOKEN)).thenReturn("admin");
        when(jwtService.isTokenValid(ADMIN_TOKEN, "admin")).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        when(jwtService.extractUsername(SELLER_TOKEN)).thenReturn("seller");
        when(jwtService.isTokenValid(SELLER_TOKEN, "seller")).thenReturn(true);
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(sellerUser));
    }

    @Test
    @DisplayName("GET /api/users con usuario administrador retorna 200 OK")
    void getUsers_asAdmin_returnsOk() throws Exception {
        UserResponse response = new UserResponse(
                sampleUserId, "User Name", "username", Role.ADMIN, true,
                Set.of(UserPermission.SECURITY_USERS.name()),
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
    @DisplayName("GET /api/users con usuario sin permiso retorna 403 Forbidden")
    void getUsers_asUnauthorizedSeller_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SELLER_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/users con datos válidos retorna 201 Created")
    void createUser_validRequest_returnsCreated() throws Exception {
        UserResponse response = new UserResponse(
                sampleUserId, "Carlos Perez", "cperez", Role.SELLER, true,
                Set.of(UserPermission.SALES_POS.name()),
                Instant.now(), "admin", null, null, null, null
        );

        when(userService.createUser(any(CreateUserRequest.class), eq("admin"))).thenReturn(response);

        String json = """
                {
                    "fullName": "Carlos Perez",
                    "username": "cperez",
                    "password": "password123",
                    "permissions": ["SALES_POS"]
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
                sampleUserId, "Carlos Perez Modificado", "cperez", Role.SELLER, true,
                Set.of(UserPermission.SALES_POS.name(), UserPermission.CATALOG_PRODUCTS.name()),
                Instant.now().minusSeconds(100), "admin", Instant.now(), "admin", null, null
        );

        when(userService.updateUser(eq(sampleUserId), any(UpdateUserRequest.class), eq("admin"))).thenReturn(response);

        String json = """
                {
                    "fullName": "Carlos Perez Modificado",
                    "permissions": ["SALES_POS", "CATALOG_PRODUCTS"]
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
    @DisplayName("GET /api/users/permissions retorna 200 OK con todas las claves de permisos")
    void getPermissions_returnsOk() throws Exception {
        mockMvc.perform(get("/api/users/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
