package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.ProductPhoto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, UUID> {

    Optional<ProductPhoto> findByProductIdAndActiveTrue(UUID productId);

    Optional<ProductPhoto> findByIdAndProductIdAndActiveTrue(UUID id, UUID productId);

    // Who waits for this lock finds the row already inactive, and so a conflict
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductPhoto p WHERE p.productId = :productId AND p.active = true")
    Optional<ProductPhoto> findActiveForUpdate(UUID productId);
}
