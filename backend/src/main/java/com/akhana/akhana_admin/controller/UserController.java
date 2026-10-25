package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.CreateUserRequest;
import com.akhana.akhana_admin.dto.UpdateUserRequest;
import com.akhana.akhana_admin.dto.UserResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('USERS')")
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(userService.getUsers(search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /** Roles and the per-action permissions each one grants. */
    @GetMapping("/roles")
    public ResponseEntity<Map<Role, Set<String>>> getRoles() {
        Map<Role, Set<String>> roles = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            roles.put(role, role.permissionKeys());
        }
        return ResponseEntity.ok(roles);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication
    ) {
        String currentUsername = extractUsername(authentication);
        UserResponse created = userService.createUser(request, currentUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        String currentUsername = extractUsername(authentication);
        UserResponse updated = userService.updateUser(id, request, currentUsername);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String currentUsername = extractUsername(authentication);
        userService.deleteUser(id, currentUsername);
        return ResponseEntity.noContent().build();
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null) {
            return "system";
        }
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
