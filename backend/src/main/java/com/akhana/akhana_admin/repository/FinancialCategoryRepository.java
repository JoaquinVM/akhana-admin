package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.FinancialCategory;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory, UUID> {

    List<FinancialCategory> findByDeletedAtIsNullOrderByNameAsc();

    List<FinancialCategory> findByTypeAndDeletedAtIsNullOrderByNameAsc(FinancialCategoryType type);

    Optional<FinancialCategory> findByIdAndDeletedAtIsNull(UUID id);

    Optional<FinancialCategory> findByIsSalesDefaultTrueAndDeletedAtIsNull();

    boolean existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull(String name, FinancialCategoryType type);

    boolean existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot(String name, FinancialCategoryType type, UUID id);
}
