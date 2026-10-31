package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import com.akhana.akhana_admin.model.FinancialTransactionStatus;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.*;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        FinancialAccountController.class,
        FinancialCategoryController.class,
        FinancialTransactionController.class,
        FinancialSummaryController.class,
        GlobalExceptionHandler.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class FinancialControllerTest {

    private static final String AUTH_TOKEN = "valid.test.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialAccountService financialAccountService;

    @MockitoBean
    private FinancialCategoryService financialCategoryService;

    @MockitoBean
    private FinancialTransactionService financialTransactionService;

    @MockitoBean
    private FinancialSummaryService financialSummaryService;

    @MockitoBean
    private com.akhana.akhana_admin.repository.FinancialAccountRepository financialAccountRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User authUser;

    @BeforeEach
    void setUp() {
        authUser = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.ADMINISTRATOR)
                .build();

        when(jwtService.extractUsername(AUTH_TOKEN)).thenReturn("admin");
        when(jwtService.isTokenValid(eq(AUTH_TOKEN), eq("admin"))).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(authUser));
    }

    @Test
    @DisplayName("GET /api/finances/accounts - Debe retornar 200 con lista de cuentas")
    void testGetAllAccounts() throws Exception {
        FinancialAccountResponse acc = FinancialAccountResponse.builder()
                .id(UUID.randomUUID())
                .name("Cuenta BISA")
                .color("#1B3B18")
                .currentBalance(new BigDecimal("1500.00"))
                .isQrAccount(true)
                .build();

        when(financialAccountService.getAllAccounts()).thenReturn(List.of(acc));

        mockMvc.perform(get("/api/finances/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cuenta BISA"))
                .andExpect(jsonPath("$[0].isQrAccount").value(true));
    }

    @Test
    @DisplayName("GET /api/finances/categories - Debe retornar 200 con lista de categorías")
    void testGetAllCategories() throws Exception {
        FinancialCategoryResponse cat = FinancialCategoryResponse.builder()
                .id(UUID.randomUUID())
                .name("Ventas")
                .type(FinancialCategoryType.INGRESO)
                .color("#2E5B27")
                .isSalesDefault(true)
                .build();

        when(financialCategoryService.getAllCategories(null)).thenReturn(List.of(cat));

        mockMvc.perform(get("/api/finances/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ventas"))
                .andExpect(jsonPath("$[0].color").value("#2E5B27"))
                .andExpect(jsonPath("$[0].isSalesDefault").value(true));
    }

    @Test
    @DisplayName("GET /api/finances/transactions - CAJ-11: filtra por días de operación from/to")
    void testGetTransactionsWithDateRange() throws Exception {
        when(financialTransactionService.getTransactions(
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 7)), isNull(), isNull(), isNull(), isNull(), isNull()
        )).thenReturn(List.of(FinancialTransactionResponse.builder()
                .id(UUID.randomUUID())
                .transactionDate(Instant.parse("2026-11-01T04:30:00Z"))
                .operationDate(LocalDate.of(2026, 10, 31))
                .build()));

        mockMvc.perform(get("/api/finances/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operationDate").value("2026-10-31"));
    }

    @Test
    @DisplayName("POST /api/finances/transactions - Debe registrar ingreso y retornar 201 CREATED")
    void testCreateTransaction() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        FinancialTransactionResponse res = FinancialTransactionResponse.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.INGRESO_MANUAL)
                .amount(new BigDecimal("300.00"))
                .description("Aporte de socios")
                .status(FinancialTransactionStatus.ACTIVO)
                .build();

        when(financialTransactionService.createTransaction(any(FinancialTransactionRequest.class), eq("admin")))
                .thenReturn(res);

        String json = """
                {
                    "type": "INGRESO_MANUAL",
                    "sourceAccountId": "%s",
                    "categoryId": "%s",
                    "amount": 300.00,
                    "description": "Aporte de socios"
                }
                """.formatted(accountId, categoryId);

        mockMvc.perform(post("/api/finances/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INGRESO_MANUAL"))
                .andExpect(jsonPath("$.amount").value(300.00));
    }

    @Test
    @DisplayName("GET /api/finances/summary - Debe retornar 200 con resumen consolidado")
    void testGetSummary() throws Exception {
        FinancialSummaryResponse summary = FinancialSummaryResponse.builder()
                .cashBalance(new BigDecimal("500.00"))
                .totalBalance(new BigDecimal("2000.00"))
                .qrAccountName("Cuenta bancaria BISA")
                .build();

        when(financialSummaryService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/finances/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(500.00))
                .andExpect(jsonPath("$.totalBalance").value(2000.00))
                .andExpect(jsonPath("$.qrAccountName").value("Cuenta bancaria BISA"));
    }

    @Test
    @DisplayName("POST /api/finances/transactions/cash-expense - Debe registrar gasto forzando cuenta Caja")
    void testCreateCashExpense() throws Exception {
        UUID categoryId = UUID.randomUUID();
        com.akhana.akhana_admin.model.FinancialAccount caja = com.akhana.akhana_admin.model.FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Caja")
                .isCashAccount(true)
                .build();

        FinancialTransactionResponse res = FinancialTransactionResponse.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .amount(new BigDecimal("45.00"))
                .description("Gasto de caja")
                .status(FinancialTransactionStatus.ACTIVO)
                .build();

        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(caja));
        when(financialTransactionService.createTransaction(any(FinancialTransactionRequest.class), eq("admin")))
                .thenReturn(res);

        String json = """
                {
                    "categoryId": "%s",
                    "amount": 45.00,
                    "description": "Gasto de caja"
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/api/finances/transactions/cash-expense")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("GASTO_MANUAL"))
                .andExpect(jsonPath("$.amount").value(45.00));
    }

    @Test
    @DisplayName("GET /api/finances/transactions/session/{sessionId} - Debe retornar gastos de la sesión de caja")
    void testGetExpensesByCashSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        FinancialTransactionResponse res = FinancialTransactionResponse.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .amount(new BigDecimal("60.00"))
                .cashSessionId(sessionId)
                .status(FinancialTransactionStatus.ACTIVO)
                .build();

        when(financialTransactionService.getExpensesByCashSession(sessionId)).thenReturn(List.of(res));

        mockMvc.perform(get("/api/finances/transactions/session/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("GASTO_MANUAL"))
                .andExpect(jsonPath("$[0].amount").value(60.00))
                .andExpect(jsonPath("$[0].cashSessionId").value(sessionId.toString()));
    }
}
