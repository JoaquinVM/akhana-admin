package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CashSessionReopening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CashSessionReopeningRepository extends JpaRepository<CashSessionReopening, UUID> {

    List<CashSessionReopening> findByCashSessionIdOrderByReopenedAtAsc(UUID cashSessionId);
}
