package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.exception.ProductPhotoStorageException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.ProductPhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CAT-12: permissions, headers and the messages the screen shows
@WebMvcTest(controllers = {ProductPhotoController.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class ProductPhotoControllerTest {

    private static final UUID PRODUCT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PHOTO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String WITH_PRODUCTS = "with.products.jwt";
    private static final String WITHOUT_PRODUCTS = "without.products.jwt";
    private static final String BASE = "/api/products/" + PRODUCT + "/photo";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProductPhotoService photoService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;

    // No real role lacks PRODUCTS, so this user carries only the POS permission
    @BeforeEach
    void setUpUsers() {
        stubUser(WITH_PRODUCTS, "con-productos", Set.of("PRODUCTS"));
        stubUser(WITHOUT_PRODUCTS, "sin-productos", Set.of("POS"));
    }

    private void stubUser(String token, String username, Set<String> permissions) {
        User user = spy(User.builder().id(UUID.randomUUID()).username(username).role(Role.OPERATOR).active(true).build());
        doReturn(permissions).when(user).getPermissions();
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.isTokenValid(token, username)).thenReturn(true);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(String token, B builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1});
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /" + "22222222-2222-2222-2222-222222222222/image", "GET /22222222-2222-2222-2222-222222222222/thumbnail",
        "POST ", "DELETE ?expectedPhotoId=22222222-2222-2222-2222-222222222222"})
    @DisplayName("Sin sesión responde 401")
    void anonymous_isUnauthorized(String route) throws Exception {
        String[] parts = route.split(" ", -1);
        mockMvc.perform(request(HttpMethod.valueOf(parts[0]), BASE + parts[1])).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /" + "22222222-2222-2222-2222-222222222222/image", "GET /22222222-2222-2222-2222-222222222222/thumbnail",
        "DELETE ?expectedPhotoId=22222222-2222-2222-2222-222222222222"})
    @DisplayName("Con sesión y sin el permiso de Productos responde 403")
    void withoutProducts_isForbidden(String route) throws Exception {
        String[] parts = route.split(" ", -1);
        mockMvc.perform(as(WITHOUT_PRODUCTS, request(HttpMethod.valueOf(parts[0]), BASE + parts[1])))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Subir sin el permiso de Productos responde 403")
    void uploadWithoutProducts_isForbidden() throws Exception {
        mockMvc.perform(as(WITHOUT_PRODUCTS, multipart(BASE).file(file()))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("La imagen sale como JPEG, en caché privada y con nosniff")
    void image_hasFixedTypeAndHeaders() throws Exception {
        when(photoService.readImage(PRODUCT, PHOTO)).thenReturn(new byte[]{1, 2});

        mockMvc.perform(as(WITH_PRODUCTS, request(HttpMethod.GET, BASE + "/" + PHOTO + "/image")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", "private, max-age=31536000, immutable"))
            .andExpect(header().string("Vary", "Authorization"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Subir responde 201 con la referencia")
    void upload_returnsPhotoId() throws Exception {
        when(photoService.upload(eq(PRODUCT), any(), eq(null), any())).thenReturn(PHOTO);

        mockMvc.perform(as(WITH_PRODUCTS, multipart(BASE).file(file())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.photoId").value(PHOTO.toString()));
    }

    @Test
    @DisplayName("Un archivo no válido responde 400 con el mensaje de la foto")
    void invalidFile_isBadRequest() throws Exception {
        when(photoService.upload(any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException(ProductPhotoService.INVALID_FILE));

        mockMvc.perform(as(WITH_PRODUCTS, multipart(BASE).file(file())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ProductPhotoService.INVALID_FILE));
    }

    @Test
    @DisplayName("Más de 10 MB responde con el mensaje de la foto, no con el de la importación")
    void maxUploadSize_usesPhotoMessage() throws Exception {
        when(photoService.upload(any(), any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(10));

        mockMvc.perform(as(WITH_PRODUCTS, multipart(BASE).file(file())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(ProductPhotoService.INVALID_FILE));
    }

    @Test
    @DisplayName("Un fallo al guardar responde 500 sin detalles técnicos")
    void storageFailure_hidesDetails() throws Exception {
        when(photoService.upload(any(), any(), any(), any()))
            .thenThrow(new ProductPhotoStorageException(ProductPhotoService.SAVE_FAILED, new RuntimeException("/data/secreto")));

        mockMvc.perform(as(WITH_PRODUCTS, multipart(BASE).file(file())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value(ProductPhotoService.SAVE_FAILED));
    }
}
