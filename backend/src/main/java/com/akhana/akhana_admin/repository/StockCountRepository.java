package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.StockCount;
import com.akhana.akhana_admin.model.StockCountStatus;
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
public interface StockCountRepository extends JpaRepository<StockCount, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM StockCount c WHERE c.id = :id")
    Optional<StockCount> findByIdForUpdate(@Param("id") UUID id);

    List<StockCount> findTop50ByOrderByCreatedAtDesc();

    List<StockCount> findByStatusOrderByCreatedAtDesc(StockCountStatus status);

    @Query(value = "SELECT nextval('stock_count_number_seq')", nativeQuery = true)
    long nextCountNumber();
}
