package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String GENERIC_AUTH_ERROR = "Invalid username or password";

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.warn("Authentication failed: User '{}' not found", username);
                return new AuthenticationFailedException(GENERIC_AUTH_ERROR);
            });

        if (!user.isActive()) {
            log.warn("Authentication failed: User '{}' is inactive", username);
            throw new AuthenticationFailedException(GENERIC_AUTH_ERROR);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Authentication failed: Incorrect password for user '{}'", username);
            throw new AuthenticationFailedException(GENERIC_AUTH_ERROR);
        }

        log.info("User '{}' successfully authenticated with role '{}'", user.getUsername(), user.getRole());

        return new LoginResponse(
            user.getId(),
            user.getUsername(),
            user.getRole(),
            "Authentication successful"
        );
    }
}
