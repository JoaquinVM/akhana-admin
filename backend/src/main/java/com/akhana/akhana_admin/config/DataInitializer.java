package com.akhana.akhana_admin.config;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initializeUserIfAbsent("admin", "12345admin", Role.ADMIN);
        initializeUserIfAbsent("seller", "12345seller", Role.SELLER);
    }

    private void initializeUserIfAbsent(String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .active(true)
                .build();

            userRepository.save(user);
            log.info("Initialized seed user '{}' with role '{}'", username, role);
        } else {
            log.debug("Seed user '{}' already exists. Skipping initialization to preserve idempotency.", username);
        }
    }
}
