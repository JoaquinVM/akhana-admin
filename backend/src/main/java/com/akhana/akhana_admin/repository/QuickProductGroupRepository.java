package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.QuickProductGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuickProductGroupRepository extends JpaRepository<QuickProductGroup, UUID> {
    // The POS reads every group with its products: one query for them, the tags and presentations batch-loaded
    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "items.product.supplier"})
    List<QuickProductGroup> findAllByOrderByDisplayOrderAsc();
}
