package com.akhana.akhana_admin.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails startup early, before any bean is created, when a required secret is missing.
 * Registered in META-INF/spring.factories; runs after config data (including the optional .env import).
 */
public class RequiredEnvironmentValidator implements EnvironmentPostProcessor {

    static final int MIN_JWT_SECRET_LENGTH = 32;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> problems = new ArrayList<>();

        if (isBlank(environment.getProperty("spring.datasource.password"))) {
            problems.add("DB_PASSWORD is not set (database password)");
        }

        String jwtSecret = environment.getProperty("security.jwt.secret");
        if (isBlank(jwtSecret)) {
            problems.add("JWT_SECRET is not set (token signing secret)");
        } else if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            problems.add("JWT_SECRET must have at least " + MIN_JWT_SECRET_LENGTH + " characters");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Missing required configuration:\n  - "
                + String.join("\n  - ", problems)
                + "\nSet them as environment variables or, for local development, copy .env.example to .env.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
