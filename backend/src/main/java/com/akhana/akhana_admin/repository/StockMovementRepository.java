package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    // INV-03: the rows of a product in a range of instants, oldest first
    @Query("SELECT m FROM StockMovement m WHERE m.productId = :productId AND m.createdAt >= :from AND m.createdAt < :to "
         + "ORDER BY m.seq ASC")
    List<StockMovement> findInRange(@Param("productId") UUID productId, @Param("from") Instant from,
                                    @Param("to") Instant to);

    // INV-03: the last row before the range, whose balance opens it
    Optional<StockMovement> findFirstByProductIdAndCreatedAtBeforeOrderBySeqDesc(UUID productId, Instant before);

    // CAT-14: the base unit is kept once the product moved stock
    boolean existsByProductId(UUID productId);

    List<StockMovement> findBySaleItemIdInOrderBySeqAsc(Collection<UUID> saleItemIds);

    List<StockMovement> findByConsumptionItemIdInOrderBySeqAsc(Collection<UUID> consumptionItemIds);

    List<StockMovement> findByPurchaseItemIdInOrderBySeqAsc(Collection<UUID> purchaseItemIds);

    // T-04: the balance of a product is the sum of its rows
    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m WHERE m.productId = :productId")
    BigDecimal sumQuantity(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(SUM(m.value), 0) FROM StockMovement m WHERE m.productId = :productId")
    BigDecimal sumValue(@Param("productId") UUID productId);
}
