package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, UUID> {

    // T-14: the stock of the products an operation moves is locked last, all together and by id
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductStock s WHERE s.productId IN :ids ORDER BY s.productId")
    List<ProductStock> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
