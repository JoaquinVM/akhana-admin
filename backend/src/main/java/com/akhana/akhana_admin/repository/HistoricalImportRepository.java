package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.HistoricalImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HistoricalImportRepository extends JpaRepository<HistoricalImport, UUID> {
}
