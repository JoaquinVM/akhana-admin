package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.CreateUserRequest;
import com.akhana.akhana_admin.dto.UpdateUserRequest;
import com.akhana.akhana_admin.dto.UserResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.model.UserPermission;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .username("testuser")
                .fullName("Test User")
                .password("encoded_pass")
                .role(Role.ADMIN)
                .active(true)
                .createdBy("admin")
                .permissions(Set.of(UserPermission.SECURITY_USERS.name()))
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
    }

    @Test
    void createUser_success() {
        CreateUserRequest request = new CreateUserRequest(
                "New User",
                "newuser",
                "raw_password",
                Set.of(UserPermission.SALES_POS.name())
        );

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("raw_password")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserResponse response = userService.createUser(request, "admin");

        assertNotNull(response);
        assertEquals("New User", response.fullName());
        assertEquals("newuser", response.username());
        assertEquals("admin", response.createdBy());
        assertTrue(response.active());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_duplicateUsername_throwsException() {
        CreateUserRequest request = new CreateUserRequest(
                "New User",
                "testuser",
                "raw_password",
                Set.of(UserPermission.SALES_POS.name())
        );

        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.createUser(request, "admin"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_withEffectiveChanges_updatesAndAudits() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest(
                "Updated Name",
                Set.of(UserPermission.SECURITY_USERS.name(), UserPermission.CATALOG_PRODUCTS.name())
        );

        UserResponse response = userService.updateUser(userId, request, "editor_user");

        assertEquals("Updated Name", response.fullName());
        assertEquals("editor_user", response.updatedBy());
        assertNotNull(response.updatedAt());
        assertEquals("testuser", response.username()); // Inmutable
        verify(userRepository).save(sampleUser);
    }

    @Test
    void updateUser_withoutChanges_skipsAuditAndUpdate() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        UpdateUserRequest request = new UpdateUserRequest(
                "Test User",
                Set.of(UserPermission.SECURITY_USERS.name())
        );

        UserResponse response = userService.updateUser(userId, request, "editor_user");

        assertEquals("Test User", response.fullName());
        assertNull(response.updatedBy());
        assertNull(response.updatedAt());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_selfDeletion_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(userId, "testuser"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_lastAdmin_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.countActiveUsersWithPermission(UserPermission.SECURITY_USERS.name())).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> userService.deleteUser(userId, "other_admin"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_success_softDeletes() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.countActiveUsersWithPermission(UserPermission.SECURITY_USERS.name())).thenReturn(2L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deleteUser(userId, "other_admin");

        assertFalse(sampleUser.isActive());
        assertNotNull(sampleUser.getDeletedAt());
        assertEquals("other_admin", sampleUser.getDeletedBy());
        verify(userRepository).save(sampleUser);
    }
}
