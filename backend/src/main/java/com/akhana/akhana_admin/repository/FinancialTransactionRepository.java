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

    Optional<FinancialTransaction> findFirstBySourceAccountIdAndType(UUID accountId, FinancialTransactionType type);

    // Any movement of the system other than the opening balance, active or voided (FIN-01); historical movements
    // do not count (FIN-11)
    @Query("SELECT COUNT(t) > 0 FROM FinancialTransaction t LEFT JOIN t.destinationAccount d " +
           "WHERE t.historicalImportId IS NULL " +
           "AND ((t.sourceAccount.id = :accountId " +
           "AND t.type <> com.akhana.akhana_admin.model.FinancialTransactionType.SALDO_INICIAL) " +
           "OR d.id = :accountId)")
    boolean existsMovementsBesidesOpeningBalance(@Param("accountId") UUID accountId);

    // Ajuste movements (opening and closing differences, CAJ-09, CAJ-10) bring the Caja account to the counted
    // cash; they are not cash entering or leaving the drawer
    String NOT_CASH_ADJUSTMENT = "AND (c.systemKey IS NULL OR c.systemKey NOT IN (" +
           "com.akhana.akhana_admin.model.FinancialCategorySystemKey.CASH_SURPLUS, " +
           "com.akhana.akhana_admin.model.FinancialCategorySystemKey.CASH_SHORTAGE)) ";

    // Cash leaving the drawer during the session: Caja expenses and transfers out of Caja (CAJ-03, T-15)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM FinancialTransaction t LEFT JOIN t.category c " +
           "WHERE t.cashSessionId = :cashSessionId " + NOT_CASH_ADJUSTMENT +
           "AND t.status = com.akhana.akhana_admin.model.FinancialTransactionStatus.ACTIVO " +
           "AND t.type IN (com.akhana.akhana_admin.model.FinancialTransactionType.GASTO_MANUAL, " +
           "com.akhana.akhana_admin.model.FinancialTransactionType.TRANSFERENCIA) " +
           "AND t.sourceAccount.isCashAccount = true")
    BigDecimal sumActiveCashOutflowsBySessionId(@Param("cashSessionId") UUID cashSessionId);

    // Cash entering the drawer during the session other than sales: transfers into Caja (CAJ-08, T-15) and
    // Caja incomes, which only come from customer collections (DEU-06)
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM FinancialTransaction t " +
           "LEFT JOIN t.destinationAccount d LEFT JOIN t.category c " +
           "WHERE t.cashSessionId = :cashSessionId " + NOT_CASH_ADJUSTMENT +
           "AND t.status = com.akhana.akhana_admin.model.FinancialTransactionStatus.ACTIVO " +
           "AND ((t.type = com.akhana.akhana_admin.model.FinancialTransactionType.TRANSFERENCIA AND d.isCashAccount = true) " +
           "OR (t.type = com.akhana.akhana_admin.model.FinancialTransactionType.INGRESO_MANUAL AND t.sourceAccount.isCashAccount = true))")
    BigDecimal sumActiveCashInflowsBySessionId(@Param("cashSessionId") UUID cashSessionId);

    // Active incomes and expenses per store month and category (FIN-10): rows of {month 'YYYY-MM', 'INGRESO' or
    // 'GASTO', category id, category name, sum}. Sales and manual incomes are incomes; transfers and opening
    // balances are left out because they do not change the consolidated balance
    @Query(value = "SELECT to_char(t.transaction_date AT TIME ZONE 'America/La_Paz', 'YYYY-MM') AS month, " +
                   "CASE WHEN t.type = 'GASTO_MANUAL' THEN 'GASTO' ELSE 'INGRESO' END AS direction, " +
                   "c.id, c.name, SUM(t.amount) " +
                   "FROM financial_transactions t LEFT JOIN financial_categories c ON c.id = t.category_id " +
                   "WHERE t.status = 'ACTIVO' AND t.type IN ('VENTA_INGRESO', 'INGRESO_MANUAL', 'GASTO_MANUAL') " +
                   "AND t.transaction_date >= :from AND t.transaction_date < :to " +
                   "GROUP BY month, direction, c.id, c.name",
           nativeQuery = true)
    List<Object[]> sumByMonthAndCategory(@Param("from") Instant from, @Param("to") Instant to);

    // Active transfers per store month and pair of accounts (FIN-10): rows of {month 'YYYY-MM', source id, source
    // name, destination id, destination name, sum}
    @Query(value = "SELECT to_char(t.transaction_date AT TIME ZONE 'America/La_Paz', 'YYYY-MM') AS month, " +
                   "s.id AS source_id, s.name AS source_name, d.id AS destination_id, d.name AS destination_name, " +
                   "SUM(t.amount) " +
                   "FROM financial_transactions t JOIN financial_accounts s ON s.id = t.source_account_id " +
                   "JOIN financial_accounts d ON d.id = t.destination_account_id " +
                   "WHERE t.status = 'ACTIVO' AND t.type = 'TRANSFERENCIA' " +
                   "AND t.transaction_date >= :from AND t.transaction_date < :to " +
                   "GROUP BY month, source_id, source_name, destination_id, destination_name",
           nativeQuery = true)
    List<Object[]> sumTransfersByMonth(@Param("from") Instant from, @Param("to") Instant to);

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
