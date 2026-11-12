package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.PurchaseShippingCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseShippingChargeRepository extends JpaRepository<PurchaseShippingCharge, UUID> {

    // Shipping of a purchase paid by a person, oldest first: the active one, if any, is the current shipping (COM-12)
    @Query("SELECT c FROM PurchaseShippingCharge c JOIN FETCH c.creditor " +
           "WHERE c.purchase.id = :purchaseId ORDER BY c.chargeDate, c.createdAt")
    List<PurchaseShippingCharge> findByPurchaseId(@Param("purchaseId") UUID purchaseId);

    // Shipping paid by a person, with its purchase and supplier, for the history (DEU-04)
    @Query("SELECT c FROM PurchaseShippingCharge c JOIN FETCH c.purchase pu JOIN FETCH pu.supplier " +
           "WHERE c.creditor.id = :creditorId ORDER BY c.operationDate, c.chargeDate, c.createdAt")
    List<PurchaseShippingCharge> findByCreditorId(@Param("creditorId") UUID creditorId);
}
