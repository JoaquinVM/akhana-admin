package com.akhana.akhana_admin.exception;

import com.akhana.akhana_admin.service.ProductImportService;
import com.akhana.akhana_admin.service.ProductPhotoService;
import com.akhana.akhana_admin.service.impl.TotpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Every error reaches the screen with the same format and a message in Spanish
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/api/test/business")
        String business() {
            throw new IllegalStateException("La caja ya está cerrada.");
        }

        @GetMapping("/api/test/library")
        String library() {
            return UUID.fromString("no-es-un-uuid").toString();
        }

        @GetMapping("/api/test/assert")
        String springAssert() {
            Assert.state(false, "Internal state broken");
            return "";
        }

        @GetMapping("/api/test/totp")
        String totp() {
            return new String(TotpServiceImpl.base32Decode("!!"));
        }

        @GetMapping("/api/test/unexpected")
        String unexpected() {
            throw new NullPointerException("Cannot invoke \"Object.toString()\" because \"value\" is null");
        }

        @GetMapping("/api/test/lock")
        String lock() {
            throw new CannotAcquireLockException("ERROR: canceling statement due to lock timeout");
        }

        @GetMapping("/api/test/pessimistic")
        String pessimistic() {
            throw new PessimisticLockingFailureException("deadlock detected");
        }

        @PostMapping("/api/test/part")
        String part(@RequestPart("file") MultipartFile file) {
            return file.getName();
        }

        @PostMapping({"/api/products/import/preview", "/api/products/{id}/photo", "/api/purchases/{id}/photo",
            "/api/purchases/readings/photo", "/api/other/upload"})
        String upload() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Un mensaje de negocio de la aplicación llega tal cual")
    void businessMessage_goesAsIs() throws Exception {
        mockMvc.perform(get("/api/test/business"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("La caja ya está cerrada."))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"/api/test/library", "/api/test/assert", "/api/test/totp"})
    @DisplayName("Un error interno (de una librería o de la propia aplicación) no muestra su mensaje en inglés")
    void internalMessage_isReplaced(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNEXPECTED_ERROR));
    }

    @Test
    @DisplayName("Cualquier otra excepción: 500 con el mensaje genérico y el mismo formato")
    void unexpected_isGeneric500() throws Exception {
        mockMvc.perform(get("/api/test/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNEXPECTED_ERROR));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"/api/test/lock", "/api/test/pessimistic"})
    @DisplayName("Un bloqueo que no se pudo tomar es un 409 que pide intentar de nuevo")
    void lockFailure_is409(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("Otra operación está en curso, intente de nuevo."));
    }

    @Test
    @DisplayName("Una dirección que no existe: 404 con el mismo formato")
    void unknownRoute_is404() throws Exception {
        mockMvc.perform(get("/api/no-existe"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.NOT_FOUND));
    }

    @Test
    @DisplayName("Un método que la dirección no admite: 405 con el mismo formato")
    void wrongMethod_is405() throws Exception {
        mockMvc.perform(delete("/api/test/business"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.error").value("Method Not Allowed"))
            .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.METHOD_NOT_ALLOWED));
    }

    @Test
    @DisplayName("Una subida sin el archivo: 400 que dice qué falta")
    void missingPart_is400() throws Exception {
        mockMvc.perform(multipart("/api/test/part").file(new MockMultipartFile("otro", new byte[]{1})))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Falta el archivo «file»."));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "/api/products/import/preview",
        "/api/products/11111111-1111-1111-1111-111111111111/photo",
        "/api/purchases/11111111-1111-1111-1111-111111111111/photo",
        "/api/purchases/readings/photo",
        "/api/other/upload"
    })
    @DisplayName("Un archivo demasiado grande recibe el mensaje de lo que admite esa subida")
    void tooLarge_messageByPathPrefix(String path) throws Exception {
        String expected = path.startsWith("/api/products/import") ? ProductImportService.INVALID_FILE
            : path.startsWith("/api/other") ? GlobalExceptionHandler.FILE_TOO_LARGE
            : ProductPhotoService.INVALID_FILE;
        mockMvc.perform(post(path))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(expected));
    }
}
