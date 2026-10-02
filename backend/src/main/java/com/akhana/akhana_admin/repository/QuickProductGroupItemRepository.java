package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.QuickProductGroupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuickProductGroupItemRepository extends JpaRepository<QuickProductGroupItem, UUID> {
    List<QuickProductGroupItem> findByGroupIdOrderByDisplayOrderAsc(UUID groupId);
    Optional<QuickProductGroupItem> findByGroupIdAndProductId(UUID groupId, UUID productId);
    void deleteByGroupIdAndProductId(UUID groupId, UUID productId);
}
