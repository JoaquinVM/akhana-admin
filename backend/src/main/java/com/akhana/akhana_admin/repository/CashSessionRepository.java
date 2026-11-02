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

    // Any session opened in the system; the historical ones (FIN-11) do not count
    boolean existsByHistoricalImportIdIsNull();

    // Money operations lock the cash session they touch before any account (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CashSession cs WHERE cs.status = :status")
    Optional<CashSession> findByStatusForUpdate(@Param("status") CashSessionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cs FROM CashSession cs WHERE cs.id = :id")
    Optional<CashSession> findByIdForUpdate(@Param("id") UUID id);

    List<CashSession> findAllByOrderByOpenedAtDesc();

    // Historical sessions (FIN-11) are left out: the first session of the system proposes the balance of Caja
    Optional<CashSession> findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus status);

    @Query("SELECT DISTINCT cs FROM CashSession cs " +
           "LEFT JOIN FETCH cs.cuts " +
           "WHERE cs.id = :id")
    Optional<CashSession> findByIdWithCuts(@Param("id") UUID id);

    // CAJ-12 to CAJ-14: opening and reopening wait for each other, so what they read about the last session and the
    // open one cannot change before they write. First thing in their transaction; nothing else takes it (T-14)
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(4801101)", nativeQuery = true)
    Integer lockCashOpening();

    // CAJ-12: a new day must be after this one, historical sessions included
    Optional<CashSession> findFirstByOrderByOperationDateDescSessionNumberDesc();

    // CAJ-14: the only session that can be reopened; opening order, so historical sessions are left out
    Optional<CashSession> findFirstByHistoricalImportIdIsNullOrderBySessionNumberDesc();
}
