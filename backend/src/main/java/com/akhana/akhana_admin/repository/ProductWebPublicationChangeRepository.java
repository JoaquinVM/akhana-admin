package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.ProductWebPublicationChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductWebPublicationChangeRepository extends JpaRepository<ProductWebPublicationChange, UUID> {

    List<ProductWebPublicationChange> findByProductIdOrderByChangedAtAsc(UUID productId);
}
