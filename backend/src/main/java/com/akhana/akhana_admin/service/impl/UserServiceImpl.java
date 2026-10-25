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
        if (userRepository.existsByUsername(cleanUsername)) {
            throw new DuplicateResourceException("El nombre de usuario '" + cleanUsername + "' ya está registrado");
        }

        User user = User.builder()
                .username(cleanUsername)
                .fullName(request.fullName().trim())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .active(true)
                .createdBy(currentUsername)
                .build();

        User saved = userRepository.save(user);
        log.info("User '{}' created by '{}'", saved.getUsername(), currentUsername);
        return mapToResponse(saved);
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

        // Si no existen cambios efectivos, no modificar campos de auditoría ni actualizar
        if (!isFullNameChanged && !isRoleChanged) {
            log.debug("No effective changes detected for user '{}'. Skipping update.", user.getUsername());
            return mapToResponse(user);
        }

        if (isRoleChanged && user.getRole() == Role.ADMINISTRATOR) {
            ensureNotLastAdministrator("No se puede quitar el rol de administrador al último administrador activo");
        }

        user.setFullName(newFullName);
        user.setRole(newRole);

        user.setUpdatedAt(Instant.now());
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

        // 1. Prohibir auto-eliminación
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("No está permitido eliminar su propia cuenta de usuario");
        }

        // 2. Prohibir eliminación si es el último administrador
        if (user.getRole() == Role.ADMINISTRATOR) {
            ensureNotLastAdministrator("No se puede eliminar al último administrador activo");
        }

        // 3. Baja lógica
        user.setActive(false);
        user.setDeletedAt(Instant.now());
        user.setDeletedBy(currentUsername);

        userRepository.save(user);
        log.info("User '{}' logically deleted by '{}'", user.getUsername(), currentUsername);
    }

    private void ensureNotLastAdministrator(String message) {
        if (userRepository.countByRoleAndActiveTrueAndDeletedAtIsNull(Role.ADMINISTRATOR) <= 1) {
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
                user.getDeletedBy()
        );
    }
}
