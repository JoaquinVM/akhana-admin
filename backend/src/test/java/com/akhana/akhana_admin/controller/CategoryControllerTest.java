package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.CategoryRequest;
import com.akhana.akhana_admin.dto.CategoryResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.CategoryStatus;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.CategoryService;
import com.akhana.akhana_admin.service.JwtService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CategoryController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class CategoryControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User adminUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.ADMIN)
                .active(true)
                .build();

        when(jwtService.extractUsername(AUTH_TOKEN)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(jwtService.isTokenValid(AUTH_TOKEN, "admin")).thenReturn(true);
    }

    @Test
    @DisplayName("GET /api/categories - Retorna 200 OK y la lista de categorías")
    void getAllCategories_Success() throws Exception {
        UUID id = UUID.randomUUID();
        CategoryResponse response = new CategoryResponse(
                id,
                "Aromaterapia",
                "Aceites y difusores",
                "#164312",
                CategoryStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(categoryService.getAllCategories(null, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Aromaterapia"))
                .andExpect(jsonPath("$[0].color").value("#164312"))
                .andExpect(jsonPath("$[0].status").value("ACTIVO"));
    }

    @Test
    @DisplayName("GET /api/categories/{id} - Retorna 200 OK cuando existe")
    void getCategoryById_Success() throws Exception {
        UUID id = UUID.randomUUID();
        CategoryResponse response = new CategoryResponse(
                id,
                "Fitoterapia",
                "Hierbas medicinales",
                "#3c6a00",
                CategoryStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(categoryService.getCategoryById(id)).thenReturn(response);

        mockMvc.perform(get("/api/categories/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Fitoterapia"));
    }

    @Test
    @DisplayName("GET /api/categories/{id} - Retorna 404 Not Found cuando no existe")
    void getCategoryById_NotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryService.getCategoryById(id))
                .thenThrow(new ResourceNotFoundException("Categoría no encontrada con id: " + id));

        mockMvc.perform(get("/api/categories/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/categories - Retorna 201 Created cuando el request es válido")
    void createCategory_Success() throws Exception {
        UUID id = UUID.randomUUID();
        CategoryResponse response = new CategoryResponse(
                id,
                "Fitoterapia",
                "Hierbas medicinales",
                "#3c6a00",
                CategoryStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(categoryService.createCategory(any(CategoryRequest.class), eq("admin"))).thenReturn(response);

        String jsonPayload = """
                {
                    "name": "Fitoterapia",
                    "description": "Hierbas medicinales",
                    "color": "#3c6a00"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Fitoterapia"))
                .andExpect(jsonPath("$.color").value("#3c6a00"));
    }

    @Test
    @DisplayName("POST /api/categories - Retorna 400 Bad Request cuando faltan campos requeridos")
    void createCategory_MissingFields_Returns400() throws Exception {
        String invalidPayload = """
                {
                    "name": "",
                    "color": ""
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/categories - Retorna 409 Conflict cuando el nombre ya existe")
    void createCategory_DuplicateName_Returns409() throws Exception {
        when(categoryService.createCategory(any(CategoryRequest.class), eq("admin")))
                .thenThrow(new DuplicateResourceException("Ya existe una categoría activa con el nombre 'Aromaterapia'"));

        String jsonPayload = """
                {
                    "name": "Aromaterapia",
                    "description": "Descripción",
                    "color": "#164312"
                }
                """;

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe una categoría activa con el nombre 'Aromaterapia'"));
    }

    @Test
    @DisplayName("PUT /api/categories/{id} - Retorna 200 OK al actualizar")
    void updateCategory_Success() throws Exception {
        UUID id = UUID.randomUUID();
        CategoryResponse response = new CategoryResponse(
                id,
                "Aromaterapia Pro",
                "Nueva descripción",
                "#5b7f52",
                CategoryStatus.ACTIVO,
                "admin",
                Instant.now(),
                "admin",
                Instant.now(),
                null,
                null
        );

        when(categoryService.updateCategory(eq(id), any(CategoryRequest.class), eq("admin")))
                .thenReturn(response);

        String jsonPayload = """
                {
                    "name": "Aromaterapia Pro",
                    "description": "Nueva descripción",
                    "color": "#5b7f52"
                }
                """;

        mockMvc.perform(put("/api/categories/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aromaterapia Pro"));
    }

    @Test
    @DisplayName("DELETE /api/categories/{id} - Retorna 204 No Content al eliminar lógicamente")
    void deleteCategory_Success() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(categoryService).deleteCategory(id, "admin");

        mockMvc.perform(delete("/api/categories/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Petición sin token retorna 401 Unauthorized")
    void requestWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }
}
