package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.ProductBarcodeRequest;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.ProductBarcodeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// CAT-10: «quién» is the name of the user, with the same principal the JWT filter sets
class ProductBarcodeControllerTest {

    private static final UUID ID = UUID.randomUUID();

    private final ProductBarcodeService service = mock(ProductBarcodeService.class);
    private final ProductBarcodeController controller = new ProductBarcodeController(service);
    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            User.builder().username("operador").build(), null, List.of());

    @Test
    void addingACodeRecordsTheUsername() {
        controller.addBarcode(ID, new ProductBarcodeRequest("7791234567898"), authentication);

        verify(service).addBarcode(ID, "7791234567898", "operador");
    }

    @Test
    void markingNoBarcodeRecordsTheUsername() {
        controller.markNoBarcode(ID, authentication);

        verify(service).markNoBarcode(ID, "operador");
    }

    @Test
    void clearingTheMarkRecordsTheUsername() {
        controller.clearNoBarcode(ID, authentication);

        verify(service).clearNoBarcode(ID, "operador");
    }
}
