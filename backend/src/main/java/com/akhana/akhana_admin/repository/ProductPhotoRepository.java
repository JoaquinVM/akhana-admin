package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.ProductPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, UUID> {

    Optional<ProductPhoto> findByProductIdAndActiveTrue(UUID productId);

    Optional<ProductPhoto> findByIdAndProductIdAndActiveTrue(UUID id, UUID productId);
}
