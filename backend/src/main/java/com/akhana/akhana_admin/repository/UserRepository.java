package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    // USR-04: the active users of a role, locked in id order, so two removals of administrators at the same time
    // cannot both see "another one is left"; the second one counts again after the first commits
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.active = true AND u.deletedAt IS NULL ORDER BY u.id")
    List<User> findActiveByRoleForUpdate(@Param("role") Role role);

    // Second factor of a login (USR-08). Queries and not save(): a login is not an edit of the user, so updated_at and
    // updated_by keep the last real change (who removed the second factor, for instance).

    /** Activates the second factor only if the user still has none; 0 when another login activated it first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.totpSecret = :secret, u.totpEnabledAt = :enabledAt, u.totpLastStep = :step " +
           "WHERE u.id = :id AND u.totpSecret IS NULL")
    int activateSecondFactor(@Param("id") UUID id, @Param("secret") String secret,
                             @Param("enabledAt") Instant enabledAt, @Param("step") long step);

    /** Keeps the step of the accepted code, so it is not accepted again. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.totpLastStep = :step WHERE u.id = :id")
    int recordSecondFactorStep(@Param("id") UUID id, @Param("step") long step);
}
