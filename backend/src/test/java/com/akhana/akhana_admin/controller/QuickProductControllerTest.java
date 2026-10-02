package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.QuickProductGroupRequest;
import com.akhana.akhana_admin.dto.QuickProductGroupResponse;
import com.akhana.akhana_admin.dto.ReorderGroupsRequest;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.QuickProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {QuickProductController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class QuickProductControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuickProductService quickProductService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User authUser;
    private UUID groupId;
    private QuickProductGroupResponse groupResponse;

    @BeforeEach
    void setUp() {
        authUser = User.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .role(Role.ADMIN)
            .active(true)
            .build();

        groupId = UUID.randomUUID();
        groupResponse = new QuickProductGroupResponse(
            groupId,
            "Bebidas",
            0,
            Instant.now(),
            Instant.now(),
            Collections.emptyList()
        );

        when(jwtService.isTokenValid(AUTH_TOKEN, "admin")).thenReturn(true);
        when(jwtService.extractUsername(AUTH_TOKEN)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(authUser));
    }

    @Test
    @DisplayName("GET /api/quick-products/groups - Debe listar grupos")
    void shouldListGroups() throws Exception {
        when(quickProductService.getAllGroups()).thenReturn(List.of(groupResponse));

        mockMvc.perform(get("/api/quick-products/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Bebidas"));
    }

    @Test
    @DisplayName("POST /api/quick-products/groups - Debe crear un nuevo grupo")
    void shouldCreateGroup() throws Exception {
        when(quickProductService.createGroup(any(QuickProductGroupRequest.class))).thenReturn(groupResponse);

        String json = """
            {
              "name": "Bebidas",
              "productIds": []
            }
            """;

        mockMvc.perform(post("/api/quick-products/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Bebidas"));
    }

    @Test
    @DisplayName("PUT /api/quick-products/groups/reorder - Debe reordenar grupos")
    void shouldReorderGroups() throws Exception {
        when(quickProductService.reorderGroups(any(ReorderGroupsRequest.class))).thenReturn(List.of(groupResponse));

        String json = """
            {
              "groupIds": ["%s"]
            }
            """.formatted(groupId);

        mockMvc.perform(put("/api/quick-products/groups/reorder")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }
}
