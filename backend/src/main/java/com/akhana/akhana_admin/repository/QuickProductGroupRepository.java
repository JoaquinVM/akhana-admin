package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.QuickProductGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuickProductGroupRepository extends JpaRepository<QuickProductGroup, UUID> {
    List<QuickProductGroup> findAllByOrderByDisplayOrderAsc();
}
