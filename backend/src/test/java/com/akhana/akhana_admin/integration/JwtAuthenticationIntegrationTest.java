package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.impl.TotpServiceImpl;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
            .build());
    }

    /** Logs in with the password and the second factor (USR-08, 069), activating it the first time. */
    private String login(String username, String password, boolean expectedMustChange) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(username))
            .andReturn().getResponse().getContentAsString();
        // Every user, operators included: the password alone gives no token
        Map<String, Object> twoFactor = JsonPath.read(body, "$.twoFactor");
        assertThat(twoFactor).isNotNull();
        assertThat((String) JsonPath.read(body, "$.token")).isNull();
        String secret = (String) twoFactor.get("secret");
        body = mockMvc.perform(post("/api/auth/login/second-factor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"%s\",\"code\":\"%s\"}"
                    .formatted(twoFactor.get("challengeId"), nextCode(username, secret))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(body, "$.mustChangePassword")).isEqualTo(expectedMustChange);
        String token = JsonPath.read(body, "$.token");
        assertThat(token).isNotBlank();
        return token;
    }

    /** The code of the authenticator app: the current step, or the next one if this step was already used. */
    private String nextCode(String username, String activationSecret) {
        User user = userRepository.findByUsername(username).orElseThrow();
        String secret = activationSecret != null ? activationSecret : user.getTotpSecret();
        long step = Instant.now().getEpochSecond() / 30;
        if (user.getTotpLastStep() != null && user.getTotpLastStep() >= step) {
            step = user.getTotpLastStep() + 1;
        }
        return TotpServiceImpl.code(TotpServiceImpl.base32Decode(secret), step);
    }

    @Test
    @DisplayName("End-to-End: Admin login generates valid JWT, which allows accessing /api/auth/me")
    void fullAuthFlow_AdminUser() throws Exception {
        User admin = createUser(Role.ADMINISTRATOR, false);

        String token = login(admin.getUsername(), PASSWORD, false);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token, admin.getUsername())).isTrue();
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMINISTRATOR");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(admin.getUsername()))
            .andExpect(jsonPath("$.role").value("ADMINISTRATOR"))
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("End-to-End: an operator enters with the second factor and gets the OPERATOR role, its permissions, and 403 on admin-only endpoints (069)")
    void fullAuthFlow_OperatorUser() throws Exception {
        User operator = createUser(Role.OPERATOR, false);

        String token = login(operator.getUsername(), PASSWORD, false);

        assertThat(jwtService.extractRole(token)).isEqualTo("OPERATOR");
        assertThat(userRepository.findByUsername(operator.getUsername()).orElseThrow().getTotpSecret()).isNotNull();

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(operator.getUsername()))
            .andExpect(jsonPath("$.role").value("OPERATOR"))
            .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("POS")))
            .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("USERS"))));

        mockMvc.perform(get("/api/finances/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("End-to-End: a user that must change the password is blocked until changing it")
    void mustChangePassword_BlocksUntilChanged() throws Exception {
        User admin = createUser(Role.ADMINISTRATOR, true);
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
            .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta."));

        String body = mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"brand-new-pass\"}".formatted(PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String newToken = com.jayway.jsonpath.JsonPath.read(body, "$.token");

        // T-19: the token issued before the change stops working; the new one continues the session
        mockMvc.perform(get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
            .andExpect(status().isOk());

        login(admin.getUsername(), "brand-new-pass", false);
    }

    @Test
    @DisplayName("End-to-End: an operator that must change the password activates the second factor first, then changes it with 12 characters (069)")
    void operatorFirstLogin_SecondFactorThenPasswordChange() throws Exception {
        User operator = createUser(Role.OPERATOR, true);
        String token = login(operator.getUsername(), PASSWORD, true);
        assertThat(userRepository.findByUsername(operator.getUsername()).orElseThrow().getTotpSecret()).isNotNull();

        mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"eleven-char\"}".formatted(PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La nueva contraseña debe tener entre 12 y 100 caracteres."));
        mockMvc.perform(post("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"twelve-chars\"}".formatted(PASSWORD)))
            .andExpect(status().isOk());

        login(operator.getUsername(), "twelve-chars", false);
    }

    @Test
    @DisplayName("Protected API: Accessing /api/auth/me without token returns 401 Unauthorized")
    void protectedEndpoint_MissingToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("La sesión no es válida o venció; vuelva a iniciar sesión."));
    }

    @Test
    @DisplayName("Protected API: Accessing /api/auth/me with invalid token returns 401 Unauthorized")
    void protectedEndpoint_InvalidToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("La sesión no es válida o venció; vuelva a iniciar sesión."));
    }

    @Test
    @DisplayName("End-to-End: the second factor secret is sent only while activating it (USR-08)")
    void secondFactorSecret_IsNeverSentAgain() throws Exception {
        User admin = createUser(Role.ADMINISTRATOR, false);
        String token = login(admin.getUsername(), PASSWORD, false);
        String secret = userRepository.findByUsername(admin.getUsername()).orElseThrow().getTotpSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");

        String users = mockMvc.perform(get("/api/users").param("search", admin.getUsername())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String user = mockMvc.perform(get("/api/users/" + admin.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.twoFactorEnabled").value(true))
            .andReturn().getResponse().getContentAsString();
        String me = mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String nextLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(admin.getUsername(), PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.twoFactor.setupRequired").value(false))
            .andReturn().getResponse().getContentAsString();

        assertThat(users).contains("\"twoFactorEnabled\":true").doesNotContain(secret);
        assertThat(user).doesNotContain(secret);
        assertThat(me).doesNotContain(secret);
        assertThat(nextLogin).doesNotContain(secret).doesNotContain("otpauth://");
    }

    @Test
    @DisplayName("End-to-End: logging in with the second factor is not an edit, the user audit keeps the last change")
    void secondFactorLogin_KeepsTheUserAudit() throws Exception {
        User admin = createUser(Role.ADMINISTRATOR, false);
        String audit = "SELECT coalesce(updated_by, '-') || ' ' || updated_at FROM app_users WHERE id = ?";
        String before = jdbcTemplate.queryForObject(audit, String.class, admin.getId());

        login(admin.getUsername(), PASSWORD, false);
        login(admin.getUsername(), PASSWORD, false);

        User stored = userRepository.findByUsername(admin.getUsername()).orElseThrow();
        assertThat(stored.getTotpSecret()).isNotNull();
        assertThat(stored.getTotpEnabledAt()).isNotNull();
        assertThat(stored.getTotpLastStep()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(audit, String.class, admin.getId())).isEqualTo(before);
    }
}

