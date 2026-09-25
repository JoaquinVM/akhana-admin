package com.akhana.akhana_admin.config;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("Debe crear usuarios admin y seller cuando la base de datos no los contiene")
    void run_WhenUsersAbsent_CreatesSeedUsers() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("seller")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("12345admin")).thenReturn("$2a$10$encodedAdmin");
        when(passwordEncoder.encode("12345seller")).thenReturn("$2a$10$encodedSeller");

        dataInitializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());

        User admin = userCaptor.getAllValues().get(0);
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getPassword()).isEqualTo("$2a$10$encodedAdmin");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();

        User seller = userCaptor.getAllValues().get(1);
        assertThat(seller.getUsername()).isEqualTo("seller");
        assertThat(seller.getPassword()).isEqualTo("$2a$10$encodedSeller");
        assertThat(seller.getRole()).isEqualTo(Role.SELLER);
        assertThat(seller.isActive()).isTrue();
    }

    @Test
    @DisplayName("Debe ser idempotente y no duplicar ni modificar usuarios si ya existen")
    void run_WhenUsersAlreadyExist_DoesNotDuplicate() {
        User existingAdmin = User.builder().username("admin").build();
        User existingSeller = User.builder().username("seller").build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingAdmin));
        when(userRepository.findByUsername("seller")).thenReturn(Optional.of(existingSeller));

        dataInitializer.run();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
