package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.service.FinancialTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {FinancialTransactionController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class FinancialTransactionWriteEndpointTest extends WriteEndpointTestSupport {

    private static final String BASE = "/api/finances/transactions";

    @MockitoBean
    private FinancialTransactionService financialTransactionService;

    @MockitoBean
    private FinancialAccountRepository financialAccountRepository;

    private static String movement(String amount, String description) {
        return """
            {"type": "GASTO_MANUAL", "sourceAccountId": "%s", "categoryId": "%s", "amount": %s, "description": "%s"}"""
            .formatted(ID, OTHER_ID, amount, description);
    }

    // One case per write endpoint: everything else in the body is valid, so the message is that field's alone
    static Stream<Arguments> invalidBodies() {
        return Stream.of(
            Arguments.of("POST", BASE, movement("0.00", "Luz"), "El monto debe ser mayor a 0."),
            Arguments.of("PUT", BASE + "/" + ID, movement("30.00", " "), "La descripción es obligatoria."),
            Arguments.of("POST", BASE + "/cash-expense", "{\"amount\": 5.00, \"description\": \"Pan\"}",
                "La categoría de gasto es obligatoria."),
            Arguments.of("POST", BASE + "/cash-transfer",
                "{\"accountId\": \"%s\", \"amount\": 100.00, \"comment\": \"Depósito\"}".formatted(ID),
                "Indica si es un ingreso o un retiro de caja."),
            Arguments.of("POST", BASE + "/cash-transfer/" + ID + "/void", "{\"voidReason\": \" \"}",
                "El motivo de anulación es obligatorio."),
            Arguments.of("POST", BASE + "/" + ID + "/void", "{\"voidReason\": \"%s\"}".formatted(longText(501)),
                "El motivo de anulación no puede exceder 500 caracteres."),
            Arguments.of("POST", BASE + "/cash-expense/" + ID + "/void", "{\"voidReason\": \"\"}",
                "El motivo de anulación es obligatorio.")
        );
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidBodies")
    @DisplayName("Un cuerpo inválido da 400 con el mensaje del campo en español y no llega al servicio")
    void invalidBodyIsRejected(String method, String url, String body, String message) throws Exception {
        send(method, url, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(financialTransactionService);
    }

    @Test
    @DisplayName("T-16: un gasto de Caja sin caja abierta da 400 con el mensaje de la regla")
    void cashMovementWithoutSessionIsRejected() throws Exception {
        String message = "Los movimientos de la cuenta Caja requieren una caja abierta.";
        when(financialTransactionService.createTransaction(any(FinancialTransactionRequest.class), eq("admin")))
            .thenAnswer(invocation -> { throw new IllegalStateException(message); });

        send("POST", BASE, movement("30.00", "Luz"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    @DisplayName("Anular sin cuerpo un movimiento de una caja cerrada da 400 con el mensaje de la regla")
    void voidingAMovementOfAClosedSessionIsRejected() throws Exception {
        String message = "No se pueden anular movimientos de una caja cerrada; corríjalos en la caja actual.";
        when(financialTransactionService.voidTransaction(eq(UUID.fromString(ID)), isNull(), eq("admin")))
            .thenAnswer(invocation -> { throw new IllegalStateException(message); });

        send("POST", BASE + "/" + ID + "/void", null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
    }
}
