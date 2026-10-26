package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.FinancialTransaction;
import com.akhana.akhana_admin.model.FinancialTransactionStatus;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    List<FinancialTransaction> findBySaleId(UUID saleId);

    List<FinancialTransaction> findByPurchaseIdOrderByTransactionDateAsc(UUID purchaseId);

    // The expense of a repayment to a person (DEU-03)
    Optional<FinancialTransaction> findByCreditorRepaymentId(UUID creditorRepaymentId);

    Optional<FinancialTransaction> findByCustomerCollectionId(UUID customerCollectionId);

    // Locked first so two voids or edits of the same movement cannot both revert it (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM FinancialTransaction t WHERE t.id = :id")
    Optional<FinancialTransaction> findByIdForUpdate(@Param("id") UUID id);

    List<FinancialTransaction> findByCashSessionIdOrderByTransactionDateDesc(UUID cashSessionId);

    // Cash leaving the drawer during the session: Caja expenses and transfers out of Caja (CAJ-03, T-15)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM FinancialTransaction t " +
           "WHERE t.cashSessionId = :cashSessionId " +
           "AND t.status = com.akhana.akhana_admin.model.FinancialTransactionStatus.ACTIVO " +
           "AND t.type IN (com.akhana.akhana_admin.model.FinancialTransactionType.GASTO_MANUAL, " +
           "com.akhana.akhana_admin.model.FinancialTransactionType.TRANSFERENCIA) " +
           "AND t.sourceAccount.isCashAccount = true")
    BigDecimal sumActiveCashOutflowsBySessionId(@Param("cashSessionId") UUID cashSessionId);

    // Cash entering the drawer during the session other than sales: transfers into Caja (CAJ-08, T-15) and
    // Caja incomes, which only come from customer collections (DEU-06)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM FinancialTransaction t " +
           "LEFT JOIN t.destinationAccount d " +
           "WHERE t.cashSessionId = :cashSessionId " +
           "AND t.status = com.akhana.akhana_admin.model.FinancialTransactionStatus.ACTIVO " +
           "AND ((t.type = com.akhana.akhana_admin.model.FinancialTransactionType.TRANSFERENCIA AND d.isCashAccount = true) " +
           "OR (t.type = com.akhana.akhana_admin.model.FinancialTransactionType.INGRESO_MANUAL AND t.sourceAccount.isCashAccount = true))")
    BigDecimal sumActiveCashInflowsBySessionId(@Param("cashSessionId") UUID cashSessionId);

    List<FinancialTransaction> findTop10ByOrderByTransactionDateDesc();

    @Query("SELECT t FROM FinancialTransaction t " +
           "WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
           "AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate " +
           "ORDER BY t.transactionDate DESC")
    List<FinancialTransaction> findByAccountAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT t FROM FinancialTransaction t " +
           "WHERE t.transactionDate >= :startDate AND t.transactionDate <= :endDate " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:accountId IS NULL OR t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
           "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR (t.notes IS NOT NULL AND LOWER(t.notes) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))) " +
           "ORDER BY t.transactionDate DESC")
    List<FinancialTransaction> filterTransactions(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("type") FinancialTransactionType type,
            @Param("accountId") UUID accountId,
            @Param("categoryId") UUID categoryId,
            @Param("status") FinancialTransactionStatus status,
            @Param("search") String search
    );
}
