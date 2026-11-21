package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.CreditorRequest;
import com.akhana.akhana_admin.dto.CustomerCollectionRequest;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.service.CreditorService;
import com.akhana.akhana_admin.service.CustomerService;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deudas: the people the store owes (creditors) and the customers who owe the store. DebtController only reads.
@WebMvcTest(controllers = {CreditorController.class, CustomerController.class, DebtController.class,
    GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class DebtWriteEndpointTest extends WriteEndpointTestSupport {

    @MockitoBean
    private CreditorService creditorService;

    @MockitoBean
    private CustomerService customerService;

    // One case per write endpoint with a body; the two deletes have none and go in the rules below
    static Stream<Arguments> invalidBodies() {
        return Stream.of(
            Arguments.of("POST", "/api/creditors", "{\"name\": \"  \"}", "El nombre de la persona es obligatorio."),
            Arguments.of("PUT", "/api/creditors/" + ID, "{\"name\": \"%s\"}".formatted(longText(151)),
                "El nombre no puede exceder 150 caracteres."),
            Arguments.of("POST", "/api/creditors/" + ID + "/repayments", "{\"amount\": 10.00}",
                "La cuenta de origen es obligatoria."),
            Arguments.of("POST", "/api/creditors/" + ID + "/repayments/" + OTHER_ID + "/void", "{\"voidReason\": \" \"}",
                "El motivo de anulación es obligatorio."),
            Arguments.of("POST", "/api/customers", "{\"name\": \"\"}", "El nombre del cliente es obligatorio."),
            Arguments.of("PUT", "/api/customers/" + ID, "{\"name\": \"Ana\", \"phone\": \"%s\"}".formatted(longText(31)),
                "El teléfono no puede exceder 30 caracteres."),
            Arguments.of("POST", "/api/customers/" + ID + "/collections", "{\"accountId\": \"%s\"}".formatted(OTHER_ID),
                "El monto es obligatorio."),
            Arguments.of("POST", "/api/customers/" + ID + "/collections/" + OTHER_ID + "/void", "{}",
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
        verifyNoInteractions(creditorService, customerService);
    }

    @Test
    @DisplayName("Una persona con un nombre que ya existe da 409 con su mensaje")
    void repeatedCreditorNameIsAConflict() throws Exception {
        when(creditorService.createCreditor(any(CreditorRequest.class), eq("admin")))
            .thenAnswer(invocation -> { throw new DuplicateResourceException("Ya existe una persona con el nombre 'Ana'."); });

        send("POST", "/api/creditors", "{\"name\": \"Ana\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Ya existe una persona con el nombre 'Ana'."));
    }

    @Test
    @DisplayName("DEU: dar de baja a una persona con deuda da 400 con el mensaje de la regla")
    void deletingACreditorWithDebtIsRejected() throws Exception {
        String message = "Ana tiene una deuda de Bs 50,00; devuélvela antes de darla de baja.";
        doAnswer(invocation -> { throw new IllegalStateException(message); })
            .when(creditorService).deleteCreditor(UUID.fromString(ID), "admin");

        send("DELETE", "/api/creditors/" + ID, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    @DisplayName("DEU: dar de baja a un cliente con saldo por cobrar da 400 con el mensaje de la regla")
    void deletingACustomerWithBalanceIsRejected() throws Exception {
        String message = "Luis tiene un saldo por cobrar de Bs 12,00.";
        doAnswer(invocation -> { throw new IllegalStateException(message); })
            .when(customerService).deleteCustomer(UUID.fromString(ID), "admin");

        send("DELETE", "/api/customers/" + ID, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    @DisplayName("Un cobro a Caja sin caja abierta da 400 con el mensaje de la regla")
    void collectionIntoCashWithoutSessionIsRejected() throws Exception {
        String message = "Los movimientos de la cuenta Caja requieren una caja abierta.";
        when(customerService.registerCollection(eq(UUID.fromString(ID)), any(CustomerCollectionRequest.class), eq("admin")))
            .thenAnswer(invocation -> { throw new IllegalStateException(message); });

        send("POST", "/api/customers/" + ID + "/collections", "{\"accountId\": \"%s\", \"amount\": 12.00}".formatted(OTHER_ID))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
    }
}
