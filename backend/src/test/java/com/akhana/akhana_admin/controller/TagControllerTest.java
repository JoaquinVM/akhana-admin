package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.TagRequest;
import com.akhana.akhana_admin.dto.TagResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.TagStatus;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.TagService;
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

@WebMvcTest(controllers = {TagController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class TagControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

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
    @DisplayName("GET /api/tags - Retorna 200 OK y la lista de etiquetas")
    void getAllTags_Success() throws Exception {
        UUID id = UUID.randomUUID();
        TagResponse response = new TagResponse(
                id,
                "Vegano",
                "#164312",
                TagStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(tagService.getAllTags(null, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Vegano"))
                .andExpect(jsonPath("$[0].color").value("#164312"))
                .andExpect(jsonPath("$[0].status").value("ACTIVO"));
    }

    @Test
    @DisplayName("GET /api/tags/{id} - Retorna 200 OK cuando existe")
    void getTagById_Success() throws Exception {
        UUID id = UUID.randomUUID();
        TagResponse response = new TagResponse(
                id,
                "Orgánico",
                "#3c6a00",
                TagStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(tagService.getTagById(id)).thenReturn(response);

        mockMvc.perform(get("/api/tags/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Orgánico"));
    }

    @Test
    @DisplayName("GET /api/tags/{id} - Retorna 404 Not Found cuando no existe")
    void getTagById_NotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(tagService.getTagById(id))
                .thenThrow(new ResourceNotFoundException("Etiqueta no encontrada con id: " + id));

        mockMvc.perform(get("/api/tags/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/tags - Retorna 201 Created cuando el request es válido")
    void createTag_Success() throws Exception {
        UUID id = UUID.randomUUID();
        TagResponse response = new TagResponse(
                id,
                "Orgánico",
                "#3c6a00",
                TagStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(tagService.createTag(any(TagRequest.class), eq("admin"))).thenReturn(response);

        String jsonPayload = """
                {
                    "name": "Orgánico",
                    "color": "#3c6a00"
                }
                """;

        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Orgánico"))
                .andExpect(jsonPath("$.color").value("#3c6a00"));
    }

    @Test
    @DisplayName("POST /api/tags - Retorna 400 Bad Request cuando faltan campos requeridos")
    void createTag_MissingFields_Returns400() throws Exception {
        String invalidPayload = """
                {
                    "name": "",
                    "color": ""
                }
                """;

        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/tags - Retorna 409 Conflict cuando el nombre ya existe")
    void createTag_DuplicateName_Returns409() throws Exception {
        when(tagService.createTag(any(TagRequest.class), eq("admin")))
                .thenThrow(new DuplicateResourceException("Ya existe una etiqueta activa con el nombre 'Vegano'"));

        String jsonPayload = """
                {
                    "name": "Vegano",
                    "color": "#164312"
                }
                """;

        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe una etiqueta activa con el nombre 'Vegano'"));
    }

    @Test
    @DisplayName("PUT /api/tags/{id} - Retorna 200 OK al actualizar")
    void updateTag_Success() throws Exception {
        UUID id = UUID.randomUUID();
        TagResponse response = new TagResponse(
                id,
                "Vegano Certificado",
                "#5b7f52",
                TagStatus.ACTIVO,
                "admin",
                Instant.now(),
                "admin",
                Instant.now(),
                null,
                null
        );

        when(tagService.updateTag(eq(id), any(TagRequest.class), eq("admin")))
                .thenReturn(response);

        String jsonPayload = """
                {
                    "name": "Vegano Certificado",
                    "color": "#5b7f52"
                }
                """;

        mockMvc.perform(put("/api/tags/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Vegano Certificado"));
    }

    @Test
    @DisplayName("DELETE /api/tags/{id} - Retorna 204 No Content al eliminar lógicamente")
    void deleteTag_Success() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(tagService).deleteTag(id, "admin");

        mockMvc.perform(delete("/api/tags/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Petición sin token retorna 401 Unauthorized")
    void requestWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isUnauthorized());
    }
}
