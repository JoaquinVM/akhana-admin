package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Sale;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only sums of the dashboard (DSH-01 to DSH-04) that the reports do not have. */
@org.springframework.stereotype.Repository
public interface DashboardRepository extends Repository<Sale, UUID> {

    // DSH-02: how the active sales of the range (by day of operation, CAJ-11, inclusive) were paid, one row of {cash, QR, credit, historical total,
    // historical sales}. Every sale type counts: it is the money that came in (or is owed) for them, as in the cash
    // session (CAJ). Historical sales (FIN-11) are one per day of the spreadsheet without how they were paid (P-32)
    @Query(value = "SELECT COALESCE(SUM(amount_cash), 0), COALESCE(SUM(amount_qr), 0), COALESCE(SUM(credit_amount), 0), " +
                   "COALESCE(SUM(total_amount) FILTER (WHERE historical_import_id IS NOT NULL), 0), " +
                   "COUNT(*) FILTER (WHERE historical_import_id IS NOT NULL) " +
                   "FROM sales WHERE status = 'COMPLETADA' AND operation_date BETWEEN :from AND :to",
           nativeQuery = true)
    List<Object[]> sumPayments(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
