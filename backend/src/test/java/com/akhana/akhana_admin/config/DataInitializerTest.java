package com.akhana.akhana_admin.config;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataInitializer initializer(String username, String password) {
        return new DataInitializer(userRepository, passwordEncoder, username, password);
    }

    @Test
    @DisplayName("Sin usuarios: crea solo el primer admin con las variables de entorno y marcado para cambiar la contraseña")
    void run_WhenNoUsers_CreatesFirstAdminOnly() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("initial-pass")).thenReturn("$2a$10$encoded");

        initializer("owner", "initial-pass").run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());

        User admin = captor.getValue();
        assertThat(admin.getUsername()).isEqualTo("owner");
        assertThat(admin.getPassword()).isEqualTo("$2a$10$encoded");
        assertThat(admin.getRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPermissions()).containsExactlyInAnyOrderElementsOf(Role.allPermissionKeys());
    }

    @Test
    @DisplayName("Con usuarios existentes: no crea ni modifica nada, aunque falten las variables del admin")
    void run_WhenUsersExist_DoesNothing() {
        when(userRepository.count()).thenReturn(3L);

        initializer("", "").run();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Sin usuarios y sin ADMIN_USERNAME/ADMIN_PASSWORD: el arranque falla con un mensaje claro")
    void run_WhenNoUsersAndMissingVariables_Fails() {
        when(userRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializer("admin", "").run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADMIN_USERNAME")
            .hasMessageContaining("ADMIN_PASSWORD");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin usuarios y ADMIN_PASSWORD corta: el arranque falla")
    void run_WhenAdminPasswordTooShort_Fails() {
        when(userRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> initializer("admin", "short").run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 8 characters");

        verify(userRepository, never()).save(any());
    }
}
