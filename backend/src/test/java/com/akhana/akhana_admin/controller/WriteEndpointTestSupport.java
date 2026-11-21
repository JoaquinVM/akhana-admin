package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * What a write endpoint answers when its body breaks a rule (400 with the field's message in Spanish) or when the
 * service rejects it (the status GlobalExceptionHandler gives). Who may call it is EndpointAuthorizationTest's job:
 * here an administrator calls every endpoint.
 */
abstract class WriteEndpointTestSupport {

    static final String AUTH_TOKEN = "valid.test.jwt.token";
    static final String ID = "11111111-1111-1111-1111-111111111111";
    static final String OTHER_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @BeforeEach
    void authenticateAdministrator() {
        User admin = User.builder().id(UUID.randomUUID()).username("admin").role(Role.ADMINISTRATOR).build();
        when(jwtService.extractUsername(AUTH_TOKEN)).thenReturn("admin");
        when(jwtService.isTokenValid(eq(AUTH_TOKEN), eq("admin"))).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
    }

    ResultActions send(String method, String url, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_TOKEN);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    static String longText(int length) {
        return "x".repeat(length);
    }
}
