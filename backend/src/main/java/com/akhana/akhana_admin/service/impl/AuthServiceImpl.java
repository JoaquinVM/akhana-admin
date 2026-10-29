package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.AuthService;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    private static final String GENERIC_AUTH_ERROR = "Invalid username or password";

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        loginAttemptService.checkNotBlocked(username, clientIp);

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("Authentication failed: User '{}' not found", username);
            throw failure(username, clientIp);
        }

        if (!user.isActive() || user.getDeletedAt() != null) {
            log.warn("Authentication failed: User '{}' is inactive or deleted", username);
            throw failure(username, clientIp);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Authentication failed: Incorrect password for user '{}'", username);
            throw failure(username, clientIp);
        }

        loginAttemptService.recordSuccess(username);
        String token = jwtService.generateToken(user);

        log.info("User '{}' successfully authenticated with role '{}'", user.getUsername(), user.getRole());

        return new LoginResponse(
            token,
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getRole(),
            user.getPermissions(),
            user.isMustChangePassword(),
            user.isMustChangePassword() ? "Password change required" : "Authentication successful"
        );
    }

    /** Records the failure (which may throw LoginBlockedException) and returns the generic error. */
    private AuthenticationFailedException failure(String username, String clientIp) {
        loginAttemptService.recordFailure(username, clientIp);
        return new AuthenticationFailedException(GENERIC_AUTH_ERROR);
    }

    @Override
    @Transactional
    public String changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser distinta de la actual");
        }
        if (request.newPassword().equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("La nueva contraseña no puede ser igual al nombre de usuario");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        // Other sessions of the user end; this one continues with the returned token
        user.setPasswordChangedAt(Instant.now());
        user.setUpdatedBy(user.getUsername());
        userRepository.save(user);

        log.info("User '{}' changed their password", user.getUsername());
        return jwtService.generateToken(user);
    }
}
