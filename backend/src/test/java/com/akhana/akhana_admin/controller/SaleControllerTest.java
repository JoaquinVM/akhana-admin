package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.SaleStatus;
import com.akhana.akhana_admin.model.SaleType;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SaleController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class SaleControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SaleService saleService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User authUser;
    private UUID saleId;
    private SaleResponse sampleResponse;

    @BeforeEach
    void setUp() {
        authUser = User.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .role(Role.ADMINISTRATOR)
            .active(true)
            .build();

        saleId = UUID.randomUUID();
        sampleResponse = new SaleResponse(
            saleId,
            "VTA-0001-001",
            UUID.randomUUID(),
            1L,
            SaleStatus.COMPLETADA,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("50.00"),
            PaymentMethod.EFECTIVO,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            "Venta normal",
            "admin",
            Instant.now(),
            null,
            null,
            null,
            Collections.emptyList(),
            SaleType.NORMAL,
            null,
            null,
            BigDecimal.ZERO
        );

        when(jwtService.isTokenValid(AUTH_TOKEN, "admin")).thenReturn(true);
        when(jwtService.extractUsername(AUTH_TOKEN)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(authUser));
    }

    @Test
    @DisplayName("POST /api/sales - Debe registrar venta detallada")
    void shouldRegisterSale() throws Exception {
        when(saleService.registerSale(any(SaleDetailRequest.class), eq("admin"))).thenReturn(sampleResponse);

        String json = """
            {
              "items": [
                {
                  "productId": "%s",
                  "quantity": 2,
                  "discountPerUnit": 0.00
                }
              ],
              "globalDiscountAmount": 0.00,
              "paymentMethod": "EFECTIVO",
              "amountCash": 50.00,
              "amountQr": 0.00,
              "amountReceived": 50.00,
              "description": "Venta normal"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/sales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.saleNumber").value("VTA-0001-001"))
            .andExpect(jsonPath("$.totalAmount").value(50.00))
            .andExpect(jsonPath("$.status").value("COMPLETADA"));
    }

    @Test
    @DisplayName("POST /api/sales/{id}/void - Debe anular venta con motivo")
    void shouldVoidSale() throws Exception {
        SaleResponse voidedResponse = new SaleResponse(
            saleId,
            "VTA-0001-001",
            UUID.randomUUID(),
            1L,
            SaleStatus.ANULADA,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("50.00"),
            PaymentMethod.EFECTIVO,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            "Venta normal",
            "admin",
            Instant.now(),
            Instant.now(),
            "admin",
            "Error en producto cobrado",
            Collections.emptyList(),
            SaleType.NORMAL,
            null,
            null,
            BigDecimal.ZERO
        );

        when(saleService.voidSale(eq(saleId), any(VoidSaleRequest.class), eq("admin"))).thenReturn(voidedResponse);

        String json = """
            {
              "voidReason": "Error en producto cobrado"
            }
            """;

        mockMvc.perform(post("/api/sales/{id}/void", saleId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANULADA"))
            .andExpect(jsonPath("$.voidReason").value("Error en producto cobrado"));
    }

    @Test
    @DisplayName("GET /api/sales - Debe listar ventas paginadas con filtros (POS-09)")
    void shouldListSales() throws Exception {
        when(saleService.searchSales(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 26), SaleStatus.ANULADA,
                SaleType.AL_COSTO, PaymentMethod.CREDITO, "admin", 1, 10))
            .thenReturn(new PageImpl<>(List.of(sampleResponse), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/sales")
                .param("from", "2026-10-01")
                .param("to", "2026-10-26")
                .param("status", "ANULADA")
                .param("saleType", "AL_COSTO")
                .param("paymentMethod", "CREDITO")
                .param("createdBy", "admin")
                .param("page", "1")
                .param("size", "10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].saleNumber").value("VTA-0001-001"))
            .andExpect(jsonPath("$.totalElements").value(11))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("GET /api/sales/sellers - Debe listar los usuarios con ventas")
    void shouldListSellers() throws Exception {
        when(saleService.getSellers()).thenReturn(List.of("admin", "operador"));

        mockMvc.perform(get("/api/sales/sellers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1]").value("operador"));
    }
}
