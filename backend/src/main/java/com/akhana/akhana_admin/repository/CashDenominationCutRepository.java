package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CashDenominationCut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CashDenominationCutRepository extends JpaRepository<CashDenominationCut, UUID> {

    // CAJ-14: the cuts of the undone closings, for the detail of the session
    List<CashDenominationCut> findByReopeningIdInOrderByDenominationDesc(Collection<UUID> reopeningIds);

    // CAJ-14: the cuts of the closing being undone stay, tied to their reopening; the session no longer sees them
    @Modifying
    @Query("UPDATE CashDenominationCut c SET c.reopeningId = :reopeningId " +
           "WHERE c.cashSession.id = :sessionId AND c.reopeningId IS NULL")
    int moveToReopening(@Param("sessionId") UUID sessionId, @Param("reopeningId") UUID reopeningId);
}
