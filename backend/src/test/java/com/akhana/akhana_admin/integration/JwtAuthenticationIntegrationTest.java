package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("End-to-End: Admin login generates valid JWT, which allows accessing /api/auth/me")
    void fullAuthFlow_AdminUser() throws Exception {
        String loginPayload = """
            {
                "username": "admin",
                "password": "12345admin"
            }
            """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String token = JsonPath.read(responseJson, "$.token");

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token, "admin")).isTrue();
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("End-to-End: Seller login generates valid JWT, which allows accessing /api/auth/me with SELLER role")
    void fullAuthFlow_SellerUser() throws Exception {
        String loginPayload = """
            {
                "username": "seller",
                "password": "12345seller"
            }
            """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.username").value("seller"))
            .andExpect(jsonPath("$.role").value("SELLER"))
            .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String token = JsonPath.read(responseJson, "$.token");

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token, "seller")).isTrue();
        assertThat(jwtService.extractRole(token)).isEqualTo("SELLER");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("seller"))
            .andExpect(jsonPath("$.role").value("SELLER"))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("Protected API: Accessing /api/auth/me without token returns 401 Unauthorized")
    void protectedEndpoint_MissingToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Authentication required or token is invalid/expired"));
    }

    @Test
    @DisplayName("Protected API: Accessing /api/auth/me with invalid token returns 401 Unauthorized")
    void protectedEndpoint_InvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Authentication required or token is invalid/expired"));
    }
}
