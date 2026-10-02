package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.CashSessionStatus;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.CashSessionService;
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

import java.math.BigDecimal;
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

@WebMvcTest(controllers = {CashSessionController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class CashSessionControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashSessionService cashSessionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private UUID sessionId;
    private CashSessionSummaryResponse summaryResponse;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        summaryResponse = new CashSessionSummaryResponse(
            sessionId,
            1L,
            CashSessionStatus.ABIERTA,
            new BigDecimal("100.00"),
            "Comentario inicial",
            "admin",
            Instant.now(),
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            null,
            0
        );

        User mockUser = User.builder()
            .id(UUID.randomUUID())
            .username("admin")
            .role(Role.ADMIN)
            .build();

        when(jwtService.extractUsername(AUTH_TOKEN)).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(mockUser));
        when(jwtService.isTokenValid(AUTH_TOKEN, "admin")).thenReturn(true);
    }

    @Test
    @DisplayName("POST /api/cash-sessions/open - Debe abrir caja exitosamente")
    void openSession_success() throws Exception {
        when(cashSessionService.openSession(any(OpenCashRequest.class), any())).thenReturn(summaryResponse);

        String json = """
            {
                "openingAmount": 100.00,
                "openingComment": "Apertura turno"
            }
            """;

        mockMvc.perform(post("/api/cash-sessions/open")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ABIERTA"))
            .andExpect(jsonPath("$.openingAmount").value(100.00));
    }

    @Test
    @DisplayName("GET /api/cash-sessions/current - Debe retornar la caja activa")
    void getCurrentSession_success() throws Exception {
        when(cashSessionService.getCurrentSession()).thenReturn(summaryResponse);

        mockMvc.perform(get("/api/cash-sessions/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ABIERTA"))
            .andExpect(jsonPath("$.openedBy").value("admin"));
    }

    @Test
    @DisplayName("POST /api/cash-sessions/current/sales - Debe registrar una venta rápida")
    void registerSale_success() throws Exception {
        SaleResponse saleResponse = new SaleResponse(
            UUID.randomUUID(),
            "VTA-0001-001",
            sessionId,
            new BigDecimal("45.00"),
            PaymentMethod.EFECTIVO,
            "Té y hierbas",
            "admin",
            Instant.now()
        );

        when(cashSessionService.registerSaleInCurrentSession(any(SaleRequest.class), any())).thenReturn(saleResponse);

        String json = """
            {
                "totalAmount": 45.00,
                "paymentMethod": "EFECTIVO",
                "description": "Té y hierbas"
            }
            """;

        mockMvc.perform(post("/api/cash-sessions/current/sales")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.saleNumber").value("VTA-0001-001"))
            .andExpect(jsonPath("$.totalAmount").value(45.00))
            .andExpect(jsonPath("$.paymentMethod").value("EFECTIVO"));
    }

    @Test
    @DisplayName("POST /api/cash-sessions/current/close - Debe cerrar caja exitosamente")
    void closeSession_success() throws Exception {
        CashSessionSummaryResponse closedSummary = new CashSessionSummaryResponse(
            sessionId,
            1L,
            CashSessionStatus.CERRADA,
            new BigDecimal("100.00"),
            "Comentario inicial",
            "admin",
            Instant.now(),
            new BigDecimal("150.00"),
            "Cierre ok",
            "admin",
            Instant.now(),
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            new BigDecimal("50.00"),
            new BigDecimal("150.00"),
            BigDecimal.ZERO,
            1
        );

        when(cashSessionService.closeCurrentSession(any(CloseCashRequest.class), any())).thenReturn(closedSummary);

        String json = """
            {
                "closingAmount": 150.00,
                "closingComment": "Cierre ok",
                "cuts": []
            }
            """;

        mockMvc.perform(post("/api/cash-sessions/current/close")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CERRADA"))
            .andExpect(jsonPath("$.closingAmount").value(150.00));
    }

    @Test
    @DisplayName("GET /api/cash-sessions - Debe listar el historial de sesiones")
    void getAllSessions_success() throws Exception {
        when(cashSessionService.getAllSessions()).thenReturn(List.of(summaryResponse));

        mockMvc.perform(get("/api/cash-sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(sessionId.toString()));
    }

    @Test
    @DisplayName("GET /api/cash-sessions/{id} - Debe retornar detalle de la sesión")
    void getSessionDetail_success() throws Exception {
        CashSessionDetailResponse detailResponse = new CashSessionDetailResponse(
            sessionId,
            1L,
            CashSessionStatus.ABIERTA,
            new BigDecimal("100.00"),
            "Apertura",
            "admin",
            Instant.now(),
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            null,
            0,
            Collections.emptyList(),
            Collections.emptyList()
        );

        when(cashSessionService.getSessionDetail(sessionId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/cash-sessions/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sessionId.toString()))
            .andExpect(jsonPath("$.status").value("ABIERTA"));
    }
}
