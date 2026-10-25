package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Creditor;
import com.akhana.akhana_admin.model.CreditorStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditorRepository extends JpaRepository<Creditor, UUID> {

    boolean existsByNameIgnoreCaseAndStatusNot(String name, CreditorStatus status);

    boolean existsByNameIgnoreCaseAndStatusNotAndIdNot(String name, CreditorStatus status, UUID id);

    // Status null means active and inactive (eliminated excluded); search matches the name
    @Query("SELECT c FROM Creditor c WHERE " +
           "((:status IS NULL AND c.status <> com.akhana.akhana_admin.model.CreditorStatus.ELIMINADO) OR c.status = :status) " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY LOWER(c.name)")
    List<Creditor> search(@Param("status") CreditorStatus status, @Param("search") String search);

    // Payments and repayments of a person lock it after the purchase and before the cash session (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Creditor c WHERE c.id = :id")
    Optional<Creditor> findByIdForUpdate(@Param("id") UUID id);

    // Several people at once in a fixed order, so two voids never wait on each other (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Creditor c WHERE c.id IN :ids ORDER BY c.id")
    List<Creditor> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    /** Debt with a person (DEU-04): active purchase payments made by them minus active repayments. */
    @Query(value = "SELECT COALESCE((SELECT SUM(p.amount) FROM purchase_payments p " +
                   "WHERE p.creditor_id = :id AND p.status = 'ACTIVO'), 0) " +
                   "- COALESCE((SELECT SUM(r.amount) FROM creditor_repayments r " +
                   "WHERE r.creditor_id = :id AND r.status = 'ACTIVO'), 0)",
           nativeQuery = true)
    BigDecimal computeDebt(@Param("id") UUID id);

    /** Debt of every person with payments or repayments: rows of {creditor id, debt}. */
    @Query(value = "SELECT c.id, COALESCE((SELECT SUM(p.amount) FROM purchase_payments p " +
                   "WHERE p.creditor_id = c.id AND p.status = 'ACTIVO'), 0) " +
                   "- COALESCE((SELECT SUM(r.amount) FROM creditor_repayments r " +
                   "WHERE r.creditor_id = c.id AND r.status = 'ACTIVO'), 0) FROM creditors c",
           nativeQuery = true)
    List<Object[]> computeDebts();
}
