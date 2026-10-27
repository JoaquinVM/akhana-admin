package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductSpecialType;
import com.akhana.akhana_admin.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.supplier " +
           "LEFT JOIN FETCH p.tags " +
           "WHERE p.id = :id")
    Optional<Product> findByIdWithRelations(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.supplier " +
           "LEFT JOIN FETCH p.tags " +
           "WHERE p.status != :excludedStatus " +
           "ORDER BY p.name ASC")
    List<Product> findByStatusNotOrderByNameAsc(@Param("excludedStatus") ProductStatus excludedStatus);

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.supplier " +
           "LEFT JOIN FETCH p.tags " +
           "WHERE p.status = :status " +
           "ORDER BY p.name ASC")
    List<Product> findByStatusOrderByNameAsc(@Param("status") ProductStatus status);

    boolean existsByCodeIgnoreCaseAndStatusNot(String code, ProductStatus status);

    // POS-11: only one unidentified product that is not eliminated
    boolean existsBySpecialTypeAndStatusNot(ProductSpecialType specialType, ProductStatus status);

    // POS-13: a third-party product that keeps its owner from being deactivated
    Optional<Product> findFirstByOwnerCreditorIdAndStatusNot(UUID ownerCreditorId, ProductStatus status);

    // CAT-06: every code, deleted ones included, so the import never reuses one
    @Query("SELECT p.code FROM Product p")
    List<String> findAllCodes();

    // CAT-06: [name, supplier name] of the products that block a name (not deleted)
    @Query("SELECT p.name, s.name FROM Product p JOIN p.supplier s WHERE p.status <> :excludedStatus")
    List<Object[]> findNamesWithSupplier(@Param("excludedStatus") ProductStatus excludedStatus);

    boolean existsByCodeIgnoreCaseAndStatusNotAndIdNot(String code, ProductStatus status, UUID id);

    boolean existsByNameIgnoreCaseAndStatusNot(String name, ProductStatus status);

    boolean existsByNameIgnoreCaseAndStatusNotAndIdNot(String name, ProductStatus status, UUID id);

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.supplier " +
           "LEFT JOIN FETCH p.tags " +
           "WHERE p.status != :excludedStatus AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY p.name ASC")
    List<Product> searchProducts(@Param("term") String term, @Param("excludedStatus") ProductStatus excludedStatus);

    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.supplier " +
           "LEFT JOIN FETCH p.tags " +
           "WHERE p.status = :status AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY p.name ASC")
    List<Product> searchProductsByStatus(@Param("term") String term, @Param("status") ProductStatus status);
}
