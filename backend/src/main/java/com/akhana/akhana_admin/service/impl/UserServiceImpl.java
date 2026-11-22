package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.CreateUserRequest;
import com.akhana.akhana_admin.dto.UpdateUserRequest;
import com.akhana.akhana_admin.dto.UserResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    // 098: the one-time code to activate the second factor
    static final java.time.Duration ACTIVATION_CODE_VALIDITY = java.time.Duration.ofHours(24);
    static final String OWN_ACTIVATION_CODE = "No puedes generar tu propio código de activación. Pídeselo a otro administrador.";
    static final String ACTIVATION_CODE_WITH_SECOND_FACTOR = "Este usuario ya tiene el segundo factor activo; para darle un código nuevo, quítale primero el segundo factor.";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    static final String PASSWORD_EQUALS_USERNAME = "La contraseña no puede ser igual al nombre de usuario.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(String term, Pageable pageable) {
        String cleanTerm = (term != null) ? term.trim() : null;
        return userRepository.searchUsers(cleanTerm, pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, String currentUsername) {
        String cleanUsername = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new DuplicateResourceException("El nombre de usuario '" + cleanUsername + "' ya está registrado.");
        }
        // Same rule as the password change (USR-06)
        if (request.password().equalsIgnoreCase(cleanUsername)) {
            throw new IllegalArgumentException(PASSWORD_EQUALS_USERNAME);
        }

        User user = User.builder()
                .username(cleanUsername)
                .fullName(request.fullName().trim())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .active(true)
                // 098: whoever created the user knows the password, so the user changes it on the first login
                .mustChangePassword(true)
                .createdBy(currentUsername)
                .build();
        String code = newActivationCode(user);

        // Flushed so the response carries the creation time the database stamps
        User saved = userRepository.saveAndFlush(user);
        log.info("User '{}' created by '{}'", saved.getUsername(), currentUsername);
        return mapToResponse(saved).withActivationCode(code);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, String currentUsername) {
        User user = userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        String newFullName = request.fullName().trim();
        Role newRole = request.role();

        boolean isFullNameChanged = !newFullName.equals(user.getFullName());
        boolean isRoleChanged = newRole != user.getRole();

        // Without effective changes the audit fields are left untouched
        if (!isFullNameChanged && !isRoleChanged) {
            log.debug("No effective changes detected for user '{}'. Skipping update.", user.getUsername());
            return mapToResponse(user);
        }

        if (isRoleChanged && user.getRole() == Role.ADMINISTRATOR) {
            ensureNotLastAdministrator("No se puede quitar el rol de administrador al último administrador activo.");
        }

        Instant now = Instant.now();
        user.setFullName(newFullName);
        user.setRole(newRole);
        if (isRoleChanged) {
            // The open sessions of the user end: the new role starts with a new login (T-19)
            user.setPasswordChangedAt(now);
        }

        user.setUpdatedAt(now);
        user.setUpdatedBy(currentUsername);

        User updated = userRepository.save(user);
        log.info("User '{}' updated by '{}'", updated.getUsername(), currentUsername);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id, String currentUsername) {
        User user = userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Nobody deletes their own user (USR-04)
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("No puedes eliminar tu propia cuenta de usuario.");
        }

        // Nor the last active administrator
        if (user.getRole() == Role.ADMINISTRATOR) {
            ensureNotLastAdministrator("No se puede eliminar al último administrador activo.");
        }

        // Logical delete (RG-01)
        user.setActive(false);
        user.setDeletedAt(Instant.now());
        user.setDeletedBy(currentUsername);

        userRepository.save(user);
        log.info("User '{}' logically deleted by '{}'", user.getUsername(), currentUsername);
    }

    @Override
    @Transactional
    public UserResponse resetSecondFactor(UUID id, String currentUsername) {
        User user = userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Like the user's own removal (USR-04): whoever holds a stolen session cannot drop the second factor
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("No puedes quitar tu propio segundo factor. Pídeselo a otro administrador.");
        }
        if (user.getTotpSecret() == null) {
            throw new IllegalArgumentException("Este usuario no tiene el segundo factor activo.");
        }

        user.setTotpSecret(null);
        user.setTotpEnabledAt(null);
        user.setTotpLastStep(null);
        // 098: activating it again needs a code from an administrator
        String code = newActivationCode(user);
        // The open sessions of the user end too, like the PWA of a lost phone (T-19)
        Instant now = Instant.now();
        user.setPasswordChangedAt(now);
        user.setUpdatedAt(now);
        user.setUpdatedBy(currentUsername);

        User updated = userRepository.save(user);
        log.info("Second factor of user '{}' removed by '{}'", updated.getUsername(), currentUsername);
        return mapToResponse(updated).withActivationCode(code);
    }

    @Override
    @Transactional
    public UserResponse generateActivationCode(UUID id, String currentUsername) {
        User user = userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException(OWN_ACTIVATION_CODE);
        }
        if (user.getTotpSecret() != null) {
            throw new IllegalArgumentException(ACTIVATION_CODE_WITH_SECOND_FACTOR);
        }
        // A new code replaces the previous one, which stops working
        String code = newActivationCode(user);
        user.setUpdatedAt(Instant.now());
        user.setUpdatedBy(currentUsername);
        User updated = userRepository.save(user);
        log.info("Activation code of user '{}' generated by '{}'", updated.getUsername(), currentUsername);
        return mapToResponse(updated).withActivationCode(code);
    }

    // 098: 8 random digits; only their hash is kept, for 24 hours
    private String newActivationCode(User user) {
        String code = String.format("%08d", RANDOM.nextInt(100_000_000));
        user.setActivationCodeRequired(true);
        user.setActivationCodeHash(passwordEncoder.encode(code));
        user.setActivationCodeExpiresAt(Instant.now().plus(ACTIVATION_CODE_VALIDITY));
        return code;
    }

    private void ensureNotLastAdministrator(String message) {
        if (userRepository.findActiveByRoleForUpdate(Role.ADMINISTRATOR).size() <= 1) {
            throw new IllegalStateException(message);
        }
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getPermissions(),
                user.getCreatedAt(),
                user.getCreatedBy(),
                user.getUpdatedAt(),
                user.getUpdatedBy(),
                user.getDeletedAt(),
                user.getDeletedBy(),
                user.getTotpSecret() != null,
                user.getTotpSecret() == null && user.isActivationCodeRequired(),
                null
        );
    }
}
