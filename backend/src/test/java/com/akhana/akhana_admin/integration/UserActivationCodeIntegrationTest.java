package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.AuthService;
import com.akhana.akhana_admin.service.UserService;
import com.akhana.akhana_admin.service.impl.AuthServiceImpl;
import com.akhana.akhana_admin.service.impl.TotpServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 098 against PostgreSQL: a new user needs the one-time code an administrator hands in person to activate the second
 * factor; the code is used only once and a new one replaces it. Users from before the change (V39) need none.
 */
@SpringBootTest
@Transactional
class UserActivationCodeIntegrationTest {

    private static final String PASSWORD = "integration-pass-098";
    private static final String IP = "198.51.100.7";

    @Autowired private UserService userService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private void next() {
        entityManager.flush();
        entityManager.clear();
    }

    private String newName() {
        return "it098_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UserResponse createOperator() {
        UserResponse created = userService.createUser(new CreateUserRequest("Operador 098", newName(), PASSWORD, Role.OPERATOR), "it-admin");
        next();
        return created;
    }

    private LoginResponse challenge(String username) {
        return authService.login(new LoginRequest(username, PASSWORD), IP);
    }

    private LoginResponse activate(LoginResponse challenge, String activationCode) {
        String code = TotpServiceImpl.code(TotpServiceImpl.base32Decode(challenge.twoFactor().secret()),
            Instant.now().getEpochSecond() / 30);
        LoginResponse session = authService.loginWithSecondFactor(
            new TwoFactorLoginRequest(challenge.twoFactor().challengeId(), code, activationCode), IP);
        next();
        return session;
    }

    @Test
    @DisplayName("098: el usuario nuevo debe cambiar la contraseña y no activa el 2FA sin su código; con él sí, y el código no vuelve a servir")
    void newUser_activatesOnlyWithItsCode_once() {
        UserResponse created = createOperator();
        assertThat(created.activationCode()).matches("\\d{8}");
        User stored = userRepository.findById(created.id()).orElseThrow();
        assertThat(stored.isMustChangePassword()).isTrue();
        assertThat(stored.getActivationCodeHash()).isNotEqualTo(created.activationCode());
        assertThat(passwordEncoder.matches(created.activationCode(), stored.getActivationCodeHash())).isTrue();

        LoginResponse first = challenge(created.username());
        assertThat(first.twoFactor().activationCodeRequired()).isTrue();
        assertThatThrownBy(() -> activate(first, null))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage(AuthServiceImpl.WRONG_ACTIVATION_CODE);

        LoginResponse second = challenge(created.username());
        LoginResponse session = activate(second, created.activationCode());
        assertThat(session.token()).isNotBlank();
        assertThat(session.mustChangePassword()).isTrue();

        User activated = userRepository.findById(created.id()).orElseThrow();
        assertThat(activated.getTotpSecret()).isNotNull();
        assertThat(activated.getActivationCodeHash()).isNull();
        assertThat(activated.isActivationCodeRequired()).isFalse();
    }

    @Test
    @DisplayName("098: generar un código nuevo invalida el anterior")
    void newCode_replacesThePreviousOne() {
        UserResponse created = createOperator();
        String fresh = userService.generateActivationCode(created.id(), "it-admin").activationCode();
        next();

        String old = created.activationCode().equals(fresh) ? "00000000" : created.activationCode();
        assertThatThrownBy(() -> activate(challenge(created.username()), old))
            .hasMessage(AuthServiceImpl.WRONG_ACTIVATION_CODE);
        assertThat(activate(challenge(created.username()), fresh).token()).isNotBlank();
    }

    @Test
    @DisplayName("098: un código vencido no activa el 2FA")
    void expiredCode_doesNotActivate() {
        UserResponse created = createOperator();
        jdbc.update("UPDATE app_users SET activation_code_expires_at = now() - interval '1 minute' WHERE id = ?", created.id());
        next();

        assertThatThrownBy(() -> activate(challenge(created.username()), created.activationCode()))
            .hasMessage(AuthServiceImpl.WRONG_ACTIVATION_CODE);
    }

    @Test
    @DisplayName("098, V39: un usuario de antes del cambio activa el 2FA sin código")
    void userFromBeforeTheChange_needsNoCode() {
        String username = newName();
        jdbc.update("INSERT INTO app_users (username, full_name, password_hash, role, active, must_change_password, created_by) "
            + "VALUES (?, 'Antes del cambio', ?, 'OPERATOR', TRUE, FALSE, 'it')", username, passwordEncoder.encode(PASSWORD));
        next();

        LoginResponse challenge = challenge(username);
        assertThat(challenge.twoFactor().activationCodeRequired()).isFalse();
        assertThat(activate(challenge, null).token()).isNotBlank();
    }
}
