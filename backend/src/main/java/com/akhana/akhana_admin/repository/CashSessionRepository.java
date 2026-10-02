package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CashSession;
import com.akhana.akhana_admin.model.CashSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<CashSession> findAllByOrderByOpenedAtDesc();

    @Query("SELECT DISTINCT cs FROM CashSession cs " +
           "LEFT JOIN FETCH cs.cuts " +
           "WHERE cs.id = :id")
    Optional<CashSession> findByIdWithCuts(@Param("id") UUID id);
}
