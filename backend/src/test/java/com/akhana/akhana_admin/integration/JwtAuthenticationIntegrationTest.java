package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.model.UserPermission;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each test creates its own users inside a rolled-back transaction: no seed credentials are assumed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JwtAuthenticationIntegrationTest {

    private static final String PASSWORD = "integration-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createUser(Role role, boolean mustChangePassword) {
        String username = "it_" + role.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.builder()
            .username(username)
            .fullName("Integration " + role.name())
            .password(passwordEncoder.encode(PASSWORD))
            .role(role)
            .active(true)
            .mustChangePassword(mustChangePassword)
            .createdBy("TEST")
            .permissions(new HashSet<>(UserPermission.allPermissionKeys()))
            .build());
    }

    private String login(String username, String password, boolean expectedMustChange) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.mustChangePassword").value(expectedMustChange))
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    @DisplayName("End-to-End: Admin login generates valid JWT, which allows accessing /api/auth/me")
    void fullAuthFlow_AdminUser() throws Exception {
        User admin = createUser(Role.ADMIN, false);

        String token = login(admin.getUsername(), PASSWORD, false);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token, admin.getUsername())).isTrue();
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(admin.getUsername()))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("End-to-End: Seller login generates valid JWT with SELLER role")
    void fullAuthFlow_SellerUser() throws Exception {
        User seller = createUser(Role.SELLER, false);

        String token = login(seller.getUsername(), PASSWORD, false);

        assertThat(jwtService.extractRole(token)).isEqualTo("SELLER");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(seller.getUsername()))
            .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    @DisplayName("End-to-End: a user that must change the password is blocked until changing it")
    void mustChangePassword_BlocksUntilChanged() throws Exception {
        User admin = createUser(Role.ADMIN, true);
        String token = login(admin.getUsername(), PASSWORD, true);

        mockMvc.perform(get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("PasswordChangeRequired"));

        mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"brand-new-pass\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));

        mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"brand-new-pass\"}".formatted(PASSWORD)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());

        login(admin.getUsername(), "brand-new-pass", false);
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
