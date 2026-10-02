package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Product;
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
