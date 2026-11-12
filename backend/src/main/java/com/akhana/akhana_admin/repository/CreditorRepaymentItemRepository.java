package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CreditorRepaymentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** What each repayment to a person paid (DEU-03); only rows of active repayments count. */
@Repository
public interface CreditorRepaymentItemRepository extends JpaRepository<CreditorRepaymentItem, UUID> {

    // Every row of the person's repayments, active or voided, with what they paid, for the history (DEU-04)
    @Query("SELECT ri FROM CreditorRepaymentItem ri JOIN FETCH ri.repayment r " +
           "LEFT JOIN FETCH ri.purchasePayment pp LEFT JOIN FETCH pp.purchase " +
           "LEFT JOIN FETCH ri.shippingCharge sc LEFT JOIN FETCH sc.purchase " +
           "LEFT JOIN FETCH ri.sale " +
           "WHERE r.creditor.id = :creditorId")
    List<CreditorRepaymentItem> findByCreditorId(@Param("creditorId") UUID creditorId);

    // A purchase payment, shipping or sale already paid to the person cannot be voided (DEU-03)
    @Query("SELECT COUNT(ri) > 0 FROM CreditorRepaymentItem ri WHERE ri.purchasePayment.id IN :ids " +
           "AND ri.repayment.status = com.akhana.akhana_admin.model.CreditorRepaymentStatus.ACTIVO")
    boolean existsActiveForPurchasePayments(@Param("ids") Collection<UUID> ids);

    @Query("SELECT COUNT(ri) > 0 FROM CreditorRepaymentItem ri WHERE ri.shippingCharge.id IN :ids " +
           "AND ri.repayment.status = com.akhana.akhana_admin.model.CreditorRepaymentStatus.ACTIVO")
    boolean existsActiveForShippingCharges(@Param("ids") Collection<UUID> ids);

    @Query("SELECT COUNT(ri) > 0 FROM CreditorRepaymentItem ri WHERE ri.sale.id = :saleId " +
           "AND ri.repayment.creditor.id = :creditorId " +
           "AND ri.repayment.status = com.akhana.akhana_admin.model.CreditorRepaymentStatus.ACTIVO")
    boolean existsActiveForSale(@Param("saleId") UUID saleId, @Param("creditorId") UUID creditorId);
}
