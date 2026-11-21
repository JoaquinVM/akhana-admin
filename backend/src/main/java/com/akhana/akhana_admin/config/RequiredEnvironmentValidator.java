package com.akhana.akhana_admin.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Fails startup early, before any bean is created, when a required secret is missing.
 * Registered in META-INF/spring.factories; runs after config data (including the optional .env import).
 */
public class RequiredEnvironmentValidator implements EnvironmentPostProcessor {

    static final int MIN_JWT_SECRET_LENGTH = 32;
    // SHA-256 of example secrets that were public in this repository (the one starting with 404E6352): anyone can
    // sign a valid token with them. Kept as hashes so the secret itself does not come back to the code
    static final Set<String> KNOWN_PUBLIC_SECRET_HASHES = Set.of(
        "3354cfcf65c7b2e6840ea6f880aede239750cb1d063950c4e8fd9fbf9ec73a32");

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
        } else if (KNOWN_PUBLIC_SECRET_HASHES.contains(sha256(jwtSecret))) {
            problems.add("JWT_SECRET is a published example value; generate a new one (openssl rand -hex 32)");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Missing required configuration:\n  - "
                + String.join("\n  - ", problems)
                + "\nSet them as environment variables or, for local development, copy .env.example to .env.");
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
