package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CustomerCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerCollectionRepository extends JpaRepository<CustomerCollection, UUID> {

    @Query("SELECT k FROM CustomerCollection k JOIN FETCH k.account WHERE k.customer.id = :customerId " +
           "ORDER BY k.collectionDate, k.createdAt")
    List<CustomerCollection> findByCustomerId(@Param("customerId") UUID customerId);
}
