package com.akhana.akhana_admin.config;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first admin only when there are no users at all, from ADMIN_USERNAME / ADMIN_PASSWORD.
 * The admin must change that password on first login.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.username:}") String adminUsername,
            @Value("${app.bootstrap.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.debug("Users already exist. Skipping first admin creation.");
            return;
        }

        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("There are no users and the first admin cannot be created: "
                + "set ADMIN_USERNAME and ADMIN_PASSWORD (environment variables or .env).");
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        User admin = User.builder()
            .username(adminUsername.trim())
            .fullName("Administrador")
            .password(passwordEncoder.encode(adminPassword))
            .role(Role.ADMINISTRATOR)
            .active(true)
            .mustChangePassword(true)
            .createdBy("SYSTEM")
            .build();

        userRepository.save(admin);
        log.info("Created first admin '{}'; the password must be changed on first login", admin.getUsername());
    }
}
