package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.StockCountLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockCountLineRepository extends JpaRepository<StockCountLine, UUID> {

    Optional<StockCountLine> findByStockCountIdAndProductId(UUID stockCountId, UUID productId);
}
