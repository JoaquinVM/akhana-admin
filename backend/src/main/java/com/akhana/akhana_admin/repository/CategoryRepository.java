package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Category;
import com.akhana.akhana_admin.model.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByStatusNotOrderByNameAsc(CategoryStatus status);

    List<Category> findByStatusOrderByNameAsc(CategoryStatus status);

    boolean existsByNameIgnoreCaseAndStatusNot(String name, CategoryStatus status);

    boolean existsByNameIgnoreCaseAndStatusNotAndIdNot(String name, CategoryStatus status, UUID id);

    @Query("SELECT c FROM Category c WHERE c.status != :excludedStatus AND " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "ORDER BY c.name ASC")
    List<Category> searchCategories(@Param("term") String term, @Param("excludedStatus") CategoryStatus excludedStatus);

    @Query("SELECT c FROM Category c WHERE c.status = :status AND " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "ORDER BY c.name ASC")
    List<Category> searchCategoriesByStatus(@Param("term") String term, @Param("status") CategoryStatus status);
}
