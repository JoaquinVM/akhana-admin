package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.SupplierRequest;
import com.akhana.akhana_admin.dto.SupplierResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.SupplierStatus;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.SupplierService;
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

@WebMvcTest(controllers = {SupplierController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class SupplierControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierService supplierService;

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
    @DisplayName("TEST-CTRL-01: GET /api/suppliers - Retorna 200 OK y la lista de proveedores")
    void getAllSuppliers_Success() throws Exception {
        UUID id = UUID.randomUUID();
        SupplierResponse response = new SupplierResponse(
                id,
                "Distribuidora del Norte S.A.",
                "PROV-001",
                "Descripción",
                "+56 9 8765 4321",
                SupplierStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(supplierService.getAllSuppliers(null, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Distribuidora del Norte S.A."))
                .andExpect(jsonPath("$[0].code").value("PROV-001"))
                .andExpect(jsonPath("$[0].status").value("ACTIVO"));
    }

    @Test
    @DisplayName("TEST-CTRL-01-B: GET /api/suppliers?status=ELIMINADO - Retorna proveedores filtrados")
    void getAllSuppliers_WithStatus_Success() throws Exception {
        UUID id = UUID.randomUUID();
        SupplierResponse response = new SupplierResponse(
                id,
                "Proveedor Eliminado",
                "PROV-999",
                null,
                null,
                SupplierStatus.ELIMINADO,
                "admin",
                Instant.now(),
                null,
                null,
                "admin",
                Instant.now()
        );

        when(supplierService.getAllSuppliers(null, "ELIMINADO")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/suppliers")
                        .param("status", "ELIMINADO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].status").value("ELIMINADO"));
    }

    @Test
    @DisplayName("TEST-CTRL-02: POST /api/suppliers - Retorna 201 Created al registrar un proveedor válido")
    void createSupplier_Success() throws Exception {
        UUID id = UUID.randomUUID();
        SupplierResponse response = new SupplierResponse(
                id,
                "Agroinsumos del Sur SpA",
                "PROV-002",
                "Descripción",
                "+56 9 1234 5678",
                SupplierStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(supplierService.createSupplier(any(SupplierRequest.class), eq("admin"))).thenReturn(response);

        String jsonPayload = """
                {
                    "name": "Agroinsumos del Sur SpA",
                    "code": "PROV-002",
                    "description": "Descripción",
                    "phone": "+56 9 1234 5678"
                }
                """;

        mockMvc.perform(post("/api/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Agroinsumos del Sur SpA"))
                .andExpect(jsonPath("$.code").value("PROV-002"))
                .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    @DisplayName("TEST-CTRL-03: POST /api/suppliers - Retorna 409 Conflict si el backend rechaza por duplicidad")
    void createSupplier_DuplicateConflict_Returns409() throws Exception {
        when(supplierService.createSupplier(any(SupplierRequest.class), eq("admin")))
                .thenThrow(new DuplicateResourceException("Ya existe un proveedor activo o inactivo con el nombre especificado."));

        String jsonPayload = """
                {
                    "name": "Distribuidora del Norte S.A.",
                    "code": "PROV-001"
                }
                """;

        mockMvc.perform(post("/api/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Ya existe un proveedor activo o inactivo con el nombre especificado."));
    }

    @Test
    @DisplayName("TEST-CTRL-03-B: POST /api/suppliers - Retorna 400 Bad Request si faltan campos obligatorios")
    void createSupplier_MissingRequiredFields_Returns400() throws Exception {
        String jsonPayload = """
                {
                    "name": "",
                    "code": ""
                }
                """;

        mockMvc.perform(post("/api/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("TEST-CTRL-04: PUT /api/suppliers/{id} - Retorna 200 OK al actualizar")
    void updateSupplier_Success() throws Exception {
        UUID id = UUID.randomUUID();
        SupplierResponse response = new SupplierResponse(
                id,
                "Nombre Actualizado",
                "PROV-001",
                "Desc",
                "+56 9 1111 2222",
                SupplierStatus.INACTIVO,
                "admin",
                Instant.now(),
                "admin",
                Instant.now(),
                null,
                null
        );

        when(supplierService.updateSupplier(eq(id), any(SupplierRequest.class), eq("admin"))).thenReturn(response);

        String jsonPayload = """
                {
                    "name": "Nombre Actualizado",
                    "code": "PROV-001",
                    "description": "Desc",
                    "phone": "+56 9 1111 2222",
                    "status": "INACTIVO"
                }
                """;

        mockMvc.perform(put("/api/suppliers/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre Actualizado"))
                .andExpect(jsonPath("$.status").value("INACTIVO"));
    }

    @Test
    @DisplayName("TEST-CTRL-05: DELETE /api/suppliers/{id} - Retorna 204 No Content en eliminación lógica")
    void deleteSupplier_Success() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(supplierService).deleteSupplier(eq(id), eq("admin"));

        mockMvc.perform(delete("/api/suppliers/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isNoContent());
    }
}
