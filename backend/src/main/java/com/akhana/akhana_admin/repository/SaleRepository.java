package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findByCashSessionIdOrderByCreatedAtDesc(UUID cashSessionId);

    long countByCashSessionId(UUID cashSessionId);
}
