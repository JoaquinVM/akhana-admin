package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    // Usernames are unique without case, deleted users included (USR-01)
    boolean existsByUsernameIgnoreCase(String username);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND " +
           "(:term IS NULL OR :term = '' OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<User> searchUsers(@Param("term") String term, Pageable pageable);

    long countByRoleAndActiveTrueAndDeletedAtIsNull(Role role);
}
