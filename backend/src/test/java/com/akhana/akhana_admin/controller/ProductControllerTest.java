package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.ProductRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.ProductService;
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

import java.math.BigDecimal;
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

@WebMvcTest(controllers = {ProductController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class ProductControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private UUID productId;
    private ProductResponse productResponse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();

        User authUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.ADMIN)
                .active(true)
                .build();

        when(jwtService.extractUsername(AUTH_TOKEN)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(authUser));
        when(jwtService.isTokenValid(AUTH_TOKEN, "admin")).thenReturn(true);

        productResponse = new ProductResponse(
                productId,
                "PROD-001",
                "Té Matcha Ceremonial 100g",
                UUID.randomUUID(),
                "Infusiones y Té",
                "#1B3B18",
                UUID.randomUUID(),
                "Distribuidora Botánica",
                "Descripción producto",
                List.of(new ProductResponse.TagItemResponse(UUID.randomUUID(), "Orgánico", "#7BB142")),
                new BigDecimal("40.00"),
                new BigDecimal("50.00"),
                new BigDecimal("10.00"),
                new BigDecimal("25.00"),
                ProductStatus.ACTIVO,
                "admin",
                Instant.now(),
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("GET /api/products - 200 OK con lista de productos")
    void getAllProducts_Success() throws Exception {
        when(productService.getAllProducts(null, null)).thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PROD-001"))
                .andExpect(jsonPath("$[0].name").value("Té Matcha Ceremonial 100g"))
                .andExpect(jsonPath("$[0].categoryName").value("Infusiones y Té"))
                .andExpect(jsonPath("$[0].fixedProfit").value(10.00))
                .andExpect(jsonPath("$[0].percentageProfit").value(25.00));
    }

    @Test
    @DisplayName("GET /api/products/{id} - 200 OK con producto")
    void getProductById_Success() throws Exception {
        when(productService.getProductById(productId)).thenReturn(productResponse);

        mockMvc.perform(get("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.code").value("PROD-001"));
    }

    @Test
    @DisplayName("POST /api/products - 201 Created")
    void createProduct_Success() throws Exception {
        String body = """
                {
                    "code": "PROD-001",
                    "name": "Té Matcha Ceremonial 100g",
                    "categoryId": "%s",
                    "supplierId": "%s",
                    "description": "Descripción",
                    "tagIds": [],
                    "buyPrice": 40.00,
                    "sellPrice": 50.00
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        when(productService.createProduct(any(ProductRequest.class), any())).thenReturn(productResponse);

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PROD-001"));
    }

    @Test
    @DisplayName("POST /api/products - 409 Conflict si el código o nombre ya existe")
    void createProduct_Duplicate_Returns409() throws Exception {
        String body = """
                {
                    "code": "PROD-001",
                    "name": "Té Matcha",
                    "categoryId": "%s",
                    "supplierId": "%s",
                    "buyPrice": 40.00,
                    "sellPrice": 50.00
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        when(productService.createProduct(any(ProductRequest.class), any()))
                .thenThrow(new DuplicateResourceException("Ya existe un producto activo o inactivo con el código especificado."));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un producto activo o inactivo con el código especificado."));
    }

    @Test
    @DisplayName("PUT /api/products/{id} - 200 OK")
    void updateProduct_Success() throws Exception {
        String body = """
                {
                    "code": "PROD-001",
                    "name": "Té Matcha Editado",
                    "categoryId": "%s",
                    "supplierId": "%s",
                    "buyPrice": 45.00,
                    "sellPrice": 60.00,
                    "status": "ACTIVO"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), any())).thenReturn(productResponse);

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROD-001"));
    }

    @Test
    @DisplayName("PATCH /api/products/{id}/status - 200 OK")
    void changeProductStatus_Success() throws Exception {
        when(productService.changeProductStatus(eq(productId), eq(ProductStatus.INACTIVO), any()))
                .thenReturn(productResponse);

        mockMvc.perform(patch("/api/products/{id}/status", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .param("status", "INACTIVO"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/products/{id} - 204 No Content")
    void deleteProduct_Success() throws Exception {
        doNothing().when(productService).deleteProduct(eq(productId), any());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isNoContent());
    }
}
