package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Sale;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only sums of the dashboard (DSH-01 to DSH-04) that the reports do not have. */
@org.springframework.stereotype.Repository
public interface DashboardRepository extends Repository<Sale, UUID> {

    // DSH-02: how the active sales of the range were paid, one row of {cash, QR, credit}. Every sale type counts:
    // it is the money that came in (or is owed) for them, as in the cash session (CAJ)
    @Query(value = "SELECT COALESCE(SUM(amount_cash), 0), COALESCE(SUM(amount_qr), 0), COALESCE(SUM(credit_amount), 0) " +
                   "FROM sales WHERE status = 'COMPLETADA' AND created_at >= :from AND created_at < :to",
           nativeQuery = true)
    List<Object[]> sumPayments(@Param("from") Instant from, @Param("to") Instant to);

    // DSH-03: lines of the active normal sales, rows of {sale id, sale total, product id, product name, quantity
    // (kilos for a presentation, CAT-11), line subtotal, unrounded cost, normal line, free line}, so the general
    // discount can be spread over each sale's lines like the report per supplier (FIN-10, P-44)
    @Query(value = "SELECT s.id, s.total_amount, p.id, p.name, COALESCE(i.weight_kg, i.quantity), i.subtotal, " +
                   "COALESCE(i.weight_kg, i.quantity) * i.unit_cost, " + SaleRepository.NORMAL_LINE + ", " +
                   "i.free_line_type IS NOT NULL " +
                   "FROM sale_items i JOIN sales s ON s.id = i.sale_id JOIN products p ON p.id = i.product_id " +
                   "WHERE s.status = 'COMPLETADA' AND s.sale_type = 'NORMAL' " +
                   "AND s.created_at >= :from AND s.created_at < :to",
           nativeQuery = true)
    List<Object[]> findProductLines(@Param("from") Instant from, @Param("to") Instant to);
}
