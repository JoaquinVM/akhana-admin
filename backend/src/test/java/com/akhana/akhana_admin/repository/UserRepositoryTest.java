package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSearchUsersAndCountPermissions() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .username("test_" + uniqueSuffix)
                .fullName("Test User " + uniqueSuffix)
                .password("hash")
                .role(Role.ADMINISTRATOR)
                .active(true)
                .createdBy("SYSTEM")
                .build();

        userRepository.save(user);

        Page<User> searchResult = userRepository.searchUsers("Test User " + uniqueSuffix, PageRequest.of(0, 10));
        assertFalse(searchResult.isEmpty());
        assertEquals("test_" + uniqueSuffix, searchResult.getContent().get(0).getUsername());

        long adminCount = userRepository.countByRoleAndActiveTrueAndDeletedAtIsNull(Role.ADMINISTRATOR);
        assertTrue(adminCount >= 1);

        // Soft delete test
        user.setDeletedAt(Instant.now());
        user.setActive(false);
        userRepository.save(user);

        Page<User> afterDelete = userRepository.searchUsers("Test User " + uniqueSuffix, PageRequest.of(0, 10));
        assertTrue(afterDelete.isEmpty());
        assertEquals(adminCount - 1, userRepository.countByRoleAndActiveTrueAndDeletedAtIsNull(Role.ADMINISTRATOR));
    }
}
