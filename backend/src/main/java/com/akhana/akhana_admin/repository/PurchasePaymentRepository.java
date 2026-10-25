package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.PurchasePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchasePaymentRepository extends JpaRepository<PurchasePayment, UUID> {

    // Purchase payments made by a person, with their purchase and supplier, for the history (DEU-04)
    @Query("SELECT p FROM PurchasePayment p JOIN FETCH p.purchase pu JOIN FETCH pu.supplier " +
           "WHERE p.creditor.id = :creditorId ORDER BY p.paymentDate, p.createdAt")
    List<PurchasePayment> findByCreditorId(@Param("creditorId") UUID creditorId);
}
