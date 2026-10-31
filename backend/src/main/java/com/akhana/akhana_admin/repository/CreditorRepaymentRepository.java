package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CreditorRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreditorRepaymentRepository extends JpaRepository<CreditorRepayment, UUID> {

    @Query("SELECT r FROM CreditorRepayment r JOIN FETCH r.account WHERE r.creditor.id = :creditorId " +
           "ORDER BY r.operationDate, r.repaymentDate, r.createdAt")
    List<CreditorRepayment> findByCreditorId(@Param("creditorId") UUID creditorId);
}
