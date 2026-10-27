package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.SaleItem;
import com.akhana.akhana_admin.model.SaleItemAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {
    List<SaleItem> findBySaleId(UUID saleId);

    // POS-12: an unidentified line is assigned once, so it is locked while it changes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM SaleItem i WHERE i.id = :id")
    Optional<SaleItem> findByIdForUpdate(@Param("id") UUID id);

    /**
     * POS-12: the only change of a line after the sale. The line takes the product, its copy and its cost, and
     * leaves the pending state; amounts stay as sold. Only a pending line matches, so it happens once.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SaleItem i SET i.product = :product, i.productCode = :code, i.productName = :name, " +
           "i.unitCost = :unitCost, i.assignmentStatus = :status, i.assignedBy = :username, i.assignedAt = :at " +
           "WHERE i.id = :id AND i.assignmentStatus = com.akhana.akhana_admin.model.SaleItemAssignmentStatus.POR_ASIGNAR")
    int assign(@Param("id") UUID id,
               @Param("product") Product product,
               @Param("code") String code,
               @Param("name") String name,
               @Param("unitCost") BigDecimal unitCost,
               @Param("status") SaleItemAssignmentStatus status,
               @Param("username") String username,
               @Param("at") Instant at);

    // POS-13: third-party lines of a person, voided sales included, for the debt history
    @Query("SELECT i FROM SaleItem i JOIN FETCH i.sale s WHERE i.creditor.id = :creditorId ORDER BY s.createdAt")
    List<SaleItem> findThirdPartyLinesByCreditorId(@Param("creditorId") UUID creditorId);
}
