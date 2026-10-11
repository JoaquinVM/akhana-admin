package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.FinancialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
