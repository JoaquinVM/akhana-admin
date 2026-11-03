package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.PurchaseReading;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseReadingRepository extends JpaRepository<PurchaseReading, UUID> {

    @Query("SELECT COALESCE(SUM(r.costUsd), 0) FROM PurchaseReading r WHERE r.createdAt >= :from AND r.createdAt < :to")
    BigDecimal sumCostBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PurchaseReading r WHERE r.id = :id")
    Optional<PurchaseReading> findByIdForUpdate(@Param("id") UUID id);
}
