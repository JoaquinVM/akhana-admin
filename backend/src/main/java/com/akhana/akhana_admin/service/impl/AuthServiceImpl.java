package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ChangePasswordRequest;
import com.akhana.akhana_admin.dto.LoginRequest;
import com.akhana.akhana_admin.dto.LoginResponse;
import com.akhana.akhana_admin.dto.TwoFactorChallengeResponse;
import com.akhana.akhana_admin.dto.TwoFactorLoginRequest;
import com.akhana.akhana_admin.exception.AuthenticationFailedException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.AuthService;
import com.akhana.akhana_admin.service.JwtService;
import com.akhana.akhana_admin.service.LoginAttemptService;
import com.akhana.akhana_admin.service.TotpService;
import com.akhana.akhana_admin.service.TwoFactorChallengeService;
import com.akhana.akhana_admin.service.TwoFactorChallengeService.Challenge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TotpService totpService;
    private final TwoFactorChallengeService challengeService;

    private static final String GENERIC_AUTH_ERROR = "Usuario o contraseña incorrectos.";
    private static final String WRONG_CODE = "Código incorrecto.";
    private static final String CHALLENGE_ENDED = "El ingreso venció. Vuelva a ingresar su usuario y contraseña.";

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        loginAttemptService.checkNotBlocked(username, clientIp);

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("Authentication failed: User '{}' not found", username);
            throw failure(username, clientIp, GENERIC_AUTH_ERROR);
        }

        if (!user.isActive() || user.getDeletedAt() != null) {
            log.warn("Authentication failed: User '{}' is inactive or deleted", username);
            throw failure(username, clientIp, GENERIC_AUTH_ERROR);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Authentication failed: Incorrect password for user '{}'", username);
            throw failure(username, clientIp, GENERIC_AUTH_ERROR);
        }

        // Nobody gets a session with the password alone (USR-08); the failures are not reset until the code is
        // right, so they keep counting against whoever knows the password but not the code
        return secondFactorChallenge(user);
    }

    @Override
    @Transactional
    public LoginResponse loginWithSecondFactor(TwoFactorLoginRequest request, String clientIp) {
        Challenge challenge = challengeService.find(request.challengeId());
        if (challenge == null) {
            throw new AuthenticationFailedException(CHALLENGE_ENDED);
        }
        loginAttemptService.checkNotBlocked(challenge.username(), clientIp);

        User user = userRepository.findById(challenge.userId()).orElse(null);
        // The user was removed or got the second factor changed while the challenge was open
        boolean activating = challenge.pendingSecret() != null;
        if (user == null || !user.isActive() || user.getDeletedAt() != null
                || activating == (user.getTotpSecret() != null)) {
            challengeService.remove(challenge.id());
            throw new AuthenticationFailedException(CHALLENGE_ENDED);
        }

        String secret = activating ? challenge.pendingSecret() : user.getTotpSecret();
        OptionalLong step = totpService.matchingStep(secret, request.code(), user.getTotpLastStep());
        if (step.isEmpty()) {
            log.warn("Second factor failed for user '{}'", user.getUsername());
            challengeService.recordFailure(challenge.id());
            throw failure(challenge.username(), clientIp, WRONG_CODE);
        }

        challengeService.remove(challenge.id());
        if (activating) {
            if (userRepository.activateSecondFactor(user.getId(), secret, Instant.now(), step.getAsLong()) == 0) {
                throw new AuthenticationFailedException(CHALLENGE_ENDED);
            }
            log.info("User '{}' activated the second factor", user.getUsername());
        } else {
            userRepository.recordSecondFactorStep(user.getId(), step.getAsLong());
        }

        loginAttemptService.recordSuccess(challenge.username(), clientIp);
        return session(user);
    }

    private LoginResponse secondFactorChallenge(User user) {
        if (user.getTotpSecret() != null) {
            String challengeId = challengeService.create(user.getId(), user.getUsername(), null);
            return LoginResponse.secondFactor(user.getUsername(), "Ingrese el código de su app autenticadora.",
                new TwoFactorChallengeResponse(challengeId, false, null, null));
        }
        // Activation: the secret is kept only in the challenge until the user confirms a code with it
        String secret = totpService.generateSecret();
        String challengeId = challengeService.create(user.getId(), user.getUsername(), secret);
        return LoginResponse.secondFactor(user.getUsername(), "Active el segundo factor con su app autenticadora.",
            new TwoFactorChallengeResponse(challengeId, true, secret, totpService.otpauthUri(secret, user.getUsername())));
    }

    private LoginResponse session(User user) {
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
            user.isMustChangePassword() ? "Debe cambiar su contraseña." : "Sesión iniciada.",
            null
        );
    }

    /** Records the failure (which may throw LoginBlockedException) and returns the error to throw. */
    private AuthenticationFailedException failure(String username, String clientIp, String message) {
        loginAttemptService.recordFailure(username, clientIp);
        return new AuthenticationFailedException(message);
    }

    @Override
    @Transactional
    public String changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser distinta de la actual.");
        }
        if (request.newPassword().equalsIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException(UserServiceImpl.PASSWORD_EQUALS_USERNAME);
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
