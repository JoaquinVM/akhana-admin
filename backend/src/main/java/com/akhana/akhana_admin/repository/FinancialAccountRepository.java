package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.FinancialAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findByDeletedAtIsNullOrderByNameAsc();

    Optional<FinancialAccount> findByIdAndDeletedAtIsNull(UUID id);

    Optional<FinancialAccount> findByIsCashAccountTrue();

    Optional<FinancialAccount> findByIsQrAccountTrueAndDeletedAtIsNull();

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(String name, UUID id);

    Optional<FinancialAccount> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByIdAndIsCashAccountTrue(UUID id);

    // Accounts are always locked in id order, in a single query, to avoid deadlocks (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FinancialAccount a WHERE a.id IN :ids ORDER BY a.id")
    List<FinancialAccount> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FinancialAccount a " +
           "WHERE a.isCashAccount = true OR (a.isQrAccount = true AND a.deletedAt IS NULL) " +
           "ORDER BY a.id")
    List<FinancialAccount> findSaleAccountsForUpdate();
}
