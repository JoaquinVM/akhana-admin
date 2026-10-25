package com.akhana.akhana_admin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredEnvironmentValidatorTest {

    private final RequiredEnvironmentValidator validator = new RequiredEnvironmentValidator();

    @Test
    @DisplayName("Falla con un mensaje que nombra DB_PASSWORD y JWT_SECRET cuando faltan")
    void missingSecrets_Fails() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("spring.datasource.password", "")
            .withProperty("security.jwt.secret", "");

        assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DB_PASSWORD")
            .hasMessageContaining("JWT_SECRET")
            .hasMessageContaining(".env.example");
    }

    @Test
    @DisplayName("Falla si JWT_SECRET tiene menos de 32 caracteres")
    void shortJwtSecret_Fails() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("spring.datasource.password", "db-pass")
            .withProperty("security.jwt.secret", "too-short");

        assertThatThrownBy(() -> validator.postProcessEnvironment(env, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 characters");
    }

    @Test
    @DisplayName("Arranca cuando los secretos están definidos")
    void secretsPresent_Passes() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("spring.datasource.password", "db-pass")
            .withProperty("security.jwt.secret", "x".repeat(32));

        assertThatCode(() -> validator.postProcessEnvironment(env, null)).doesNotThrowAnyException();
    }
}
