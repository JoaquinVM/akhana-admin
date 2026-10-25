package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Purchase;
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
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    // Purchase numbers come from a database sequence, like sales (T-06)
    @Query(value = "SELECT nextval('purchase_number_seq')", nativeQuery = true)
    long nextPurchaseNumber();

    // A payment locks its purchase first, then the cash session and the account (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT p FROM Purchase p JOIN FETCH p.supplier s " +
           "WHERE (:supplierId IS NULL OR s.id = :supplierId) " +
           "AND (CAST(:search AS string) IS NULL " +
           "OR LOWER(p.purchaseNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR (p.comment IS NOT NULL AND LOWER(p.comment) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))) " +
           "ORDER BY p.purchaseDate DESC, p.createdAt DESC")
    List<Purchase> search(@Param("supplierId") UUID supplierId, @Param("search") String search);

    // Registered purchases with a balance, by supplier and oldest first (COM-08)
    @Query("SELECT p FROM Purchase p JOIN FETCH p.supplier s " +
           "WHERE p.status = com.akhana.akhana_admin.model.PurchaseStatus.REGISTRADO " +
           "AND p.paidAmount < p.itemsTotal " +
           "AND (:supplierId IS NULL OR s.id = :supplierId) " +
           "ORDER BY LOWER(s.name), s.id, p.purchaseDate, p.createdAt")
    List<Purchase> findPayables(@Param("supplierId") UUID supplierId);
}
