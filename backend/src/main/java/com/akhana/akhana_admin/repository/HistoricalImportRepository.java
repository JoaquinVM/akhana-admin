package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.HistoricalImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistoricalImportRepository extends JpaRepository<HistoricalImport, UUID> {

    Optional<HistoricalImport> findFirstByOrderByImportedAtAsc();

    // FIN-11 imports once: a transaction-scoped advisory lock makes a second import wait for the first and then
    // see it done, instead of both passing the "not done yet" check
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(4801100)", nativeQuery = true)
    Integer lockImport();
}
