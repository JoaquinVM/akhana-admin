package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.dto.PurchaseRequest;
import com.akhana.akhana_admin.dto.VoidPurchaseRequest;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.service.PurchaseService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PurchaseController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class PurchaseWriteEndpointTest extends WriteEndpointTestSupport {

    private static final String LINE = """
        {"productId": "%s", "quantity": 2, "unitCost": 5.00}""".formatted(OTHER_ID);

    @MockitoBean
    private PurchaseService purchaseService;

    // One case per write endpoint: everything else in the body is valid, so the message is that field's alone
    static Stream<Arguments> invalidBodies() {
        return Stream.of(
            Arguments.of("POST", "/api/purchases",
                "{\"supplierId\": \"%s\", \"items\": []}".formatted(ID),
                "El pedido debe tener al menos una línea."),
            Arguments.of("PUT", "/api/purchases/" + ID,
                "{\"supplierId\": \"%s\", \"items\": [%s], \"reason\": \"  \"}".formatted(ID, LINE),
                "El motivo del cambio es obligatorio."),
            Arguments.of("POST", "/api/purchases/" + ID + "/payments",
                "{\"accountId\": \"%s\"}".formatted(OTHER_ID),
                "El monto es obligatorio."),
            Arguments.of("PUT", "/api/purchases/" + ID + "/delivery",
                "{\"deliveryStatus\": \"PENDIENTE\"}",
                "El tipo de entrega es obligatorio."),
            Arguments.of("POST", "/api/purchases/" + ID + "/void",
                "{\"voidReason\": \"\"}",
                "El motivo de anulación es obligatorio."),
            Arguments.of("POST", "/api/purchases/" + ID + "/payments/" + OTHER_ID + "/void",
                "{\"voidReason\": \"%s\"}".formatted(longText(501)),
                "El motivo de anulación no puede exceder 500 caracteres.")
        );
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidBodies")
    @DisplayName("Un cuerpo inválido da 400 con el mensaje del campo en español y no llega al servicio")
    void invalidBodyIsRejected(String method, String url, String body, String message) throws Exception {
        send(method, url, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
        verifyNoInteractions(purchaseService);
    }

    @Test
    @DisplayName("COM-27: el mismo pedido reintentado con otro contenido da 409 con su mensaje")
    void retriedPurchaseWithOtherContentIsAConflict() throws Exception {
        String message = "Este pedido ya se registró como PED-000042 con otro contenido; revisa la lista de pedidos antes de volver a guardar.";
        when(purchaseService.registerPurchase(any(PurchaseRequest.class), eq("admin")))
            .thenAnswer(invocation -> { throw new DuplicateResourceException(message); });

        send("POST", "/api/purchases", "{\"supplierId\": \"%s\", \"items\": [%s]}".formatted(ID, LINE))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    @DisplayName("Anular un pedido con movimientos de una caja cerrada da 400 con el mensaje de la regla")
    void voidingAPurchaseOfAClosedSessionIsRejected() throws Exception {
        String message = "El pedido tiene movimientos de una caja cerrada; no se puede anular.";
        when(purchaseService.voidPurchase(eq(UUID.fromString(ID)), any(VoidPurchaseRequest.class), eq("admin")))
            .thenAnswer(invocation -> { throw new IllegalStateException(message); });

        send("POST", "/api/purchases/" + ID + "/void", "{\"voidReason\": \"Proveedor no entregó\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(message));
    }
}
