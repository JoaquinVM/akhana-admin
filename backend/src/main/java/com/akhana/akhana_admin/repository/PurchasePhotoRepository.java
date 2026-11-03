package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.PurchasePhoto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchasePhotoRepository extends JpaRepository<PurchasePhoto, UUID> {

    Optional<PurchasePhoto> findByPurchaseIdAndActiveTrue(UUID purchaseId);

    Optional<PurchasePhoto> findByIdAndPurchaseIdAndActiveTrue(UUID id, UUID purchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PurchasePhoto p WHERE p.purchaseId = :purchaseId AND p.active = true")
    Optional<PurchasePhoto> findActiveForUpdate(@Param("purchaseId") UUID purchaseId);
}
