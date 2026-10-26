package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Customer;
import com.akhana.akhana_admin.model.CustomerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByNameIgnoreCaseAndStatusNot(String name, CustomerStatus status);

    boolean existsByNameIgnoreCaseAndStatusNotAndIdNot(String name, CustomerStatus status, UUID id);

    // Status null means active and inactive (eliminated excluded); search matches the name
    @Query("SELECT c FROM Customer c WHERE " +
           "((:status IS NULL AND c.status <> com.akhana.akhana_admin.model.CustomerStatus.ELIMINADO) OR c.status = :status) " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY LOWER(c.name)")
    List<Customer> search(@Param("status") CustomerStatus status, @Param("search") String search);

    // A credit sale locks the customer after the cash session; a collection locks it first (T-14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

    /** Receivable from a customer (DEU-07): credit of their active sales minus active collections. */
    @Query(value = "SELECT COALESCE((SELECT SUM(s.credit_amount) FROM sales s " +
                   "WHERE s.customer_id = :id AND s.status = 'COMPLETADA'), 0) " +
                   "- COALESCE((SELECT SUM(k.amount) FROM customer_collections k " +
                   "WHERE k.customer_id = :id AND k.status = 'ACTIVO'), 0)",
           nativeQuery = true)
    BigDecimal computeBalance(@Param("id") UUID id);

    /** Receivable of every customer: rows of {customer id, balance}. */
    @Query(value = "SELECT c.id, COALESCE((SELECT SUM(s.credit_amount) FROM sales s " +
                   "WHERE s.customer_id = c.id AND s.status = 'COMPLETADA'), 0) " +
                   "- COALESCE((SELECT SUM(k.amount) FROM customer_collections k " +
                   "WHERE k.customer_id = c.id AND k.status = 'ACTIVO'), 0) FROM customers c",
           nativeQuery = true)
    List<Object[]> computeBalances();
}
