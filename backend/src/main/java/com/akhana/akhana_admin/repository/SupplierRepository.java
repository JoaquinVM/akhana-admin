package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Supplier;
import com.akhana.akhana_admin.model.SupplierStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findByStatusNotOrderByNameAsc(SupplierStatus status);

    List<Supplier> findByStatusOrderByNameAsc(SupplierStatus status);

    boolean existsByNameIgnoreCaseAndStatusNot(String name, SupplierStatus status);

    boolean existsByNameIgnoreCaseAndStatusNotAndIdNot(String name, SupplierStatus status, UUID id);

    boolean existsByCodeIgnoreCaseAndStatusNot(String code, SupplierStatus status);

    boolean existsByCodeIgnoreCaseAndStatusNotAndIdNot(String code, SupplierStatus status, UUID id);

    @Query("SELECT s FROM Supplier s WHERE s.status != :excludedStatus AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(s.code) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY s.name ASC")
    List<Supplier> searchSuppliers(@Param("term") String term, @Param("excludedStatus") SupplierStatus excludedStatus);

    @Query("SELECT s FROM Supplier s WHERE s.status = :status AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(s.code) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY s.name ASC")
    List<Supplier> searchSuppliersByStatus(@Param("term") String term, @Param("status") SupplierStatus status);
}
