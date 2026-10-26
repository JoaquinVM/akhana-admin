package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findByCashSessionIdOrderByCreatedAtDesc(UUID cashSessionId);

    long countByCashSessionId(UUID cashSessionId);

    // Sales with a part on credit to a customer, voided included, for the receivables history (DEU-07)
    @Query("SELECT s FROM Sale s WHERE s.customer.id = :customerId AND s.creditAmount > 0 ORDER BY s.createdAt")
    List<Sale> findCreditSalesByCustomerId(@Param("customerId") UUID customerId);

    // Sale numbers come from a database sequence so concurrent sales never share one (T-06)
    @Query(value = "SELECT nextval('sale_number_seq')", nativeQuery = true)
    long nextSaleNumber();
}
