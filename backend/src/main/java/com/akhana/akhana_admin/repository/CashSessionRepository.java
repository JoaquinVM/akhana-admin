package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CashSession;
import com.akhana.akhana_admin.model.CashSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    Optional<CashSession> findByStatus(CashSessionStatus status);

    boolean existsByStatus(CashSessionStatus status);

    // Money operations lock the cash session they touch before any account (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CashSession cs WHERE cs.status = :status")
    Optional<CashSession> findByStatusForUpdate(@Param("status") CashSessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CashSession cs WHERE cs.id = :id")
    Optional<CashSession> findByIdForUpdate(@Param("id") UUID id);

    List<CashSession> findAllByOrderByOpenedAtDesc();

    @Query("SELECT DISTINCT cs FROM CashSession cs " +
           "LEFT JOIN FETCH cs.cuts " +
           "WHERE cs.id = :id")
    Optional<CashSession> findByIdWithCuts(@Param("id") UUID id);
}
