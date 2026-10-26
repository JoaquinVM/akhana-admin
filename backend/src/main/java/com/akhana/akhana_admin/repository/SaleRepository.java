package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.Sale;
import com.akhana.akhana_admin.model.SaleStatus;
import com.akhana.akhana_admin.model.SaleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findByCashSessionIdOrderByCreatedAtDesc(UUID cashSessionId);

    long countByCashSessionId(UUID cashSessionId);

    // Sales with a part on credit to a customer, voided included, for the receivables history (DEU-07)
    @Query("SELECT s FROM Sale s WHERE s.customer.id = :customerId AND s.creditAmount > 0 ORDER BY s.createdAt")
    List<Sale> findCreditSalesByCustomerId(@Param("customerId") UUID customerId);

    // Sales history (POS-09). A method matches every sale that used it: Efectivo and QR include the mixed ones,
    // Crédito includes the partly-on-credit ones; Mixto matches only mixed sales
    @EntityGraph(attributePaths = {"cashSession", "customer"})
    @Query("SELECT s FROM Sale s " +
           "WHERE s.createdAt >= :from AND s.createdAt < :to " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:saleType IS NULL OR s.saleType = :saleType) " +
           "AND (:paymentMethod IS NULL OR s.paymentMethod = :paymentMethod " +
           "OR (:paymentMethod = com.akhana.akhana_admin.model.PaymentMethod.EFECTIVO AND s.amountCash > 0) " +
           "OR (:paymentMethod = com.akhana.akhana_admin.model.PaymentMethod.QR AND s.amountQr > 0) " +
           "OR (:paymentMethod = com.akhana.akhana_admin.model.PaymentMethod.CREDITO AND s.creditAmount > 0)) " +
           "AND (CAST(:createdBy AS string) IS NULL OR s.createdBy = :createdBy)")
    Page<Sale> search(@Param("from") Instant from,
                      @Param("to") Instant to,
                      @Param("status") SaleStatus status,
                      @Param("saleType") SaleType saleType,
                      @Param("paymentMethod") PaymentMethod paymentMethod,
                      @Param("createdBy") String createdBy,
                      Pageable pageable);

    // Users that registered at least one sale, for the history's user filter (POS-09)
    @Query("SELECT DISTINCT s.createdBy FROM Sale s ORDER BY s.createdBy")
    List<String> findSellers();

    // Active sales per store day and sale type (FIN-10, CON-03): rows of {day 'YYYY-MM-DD', sale type, sales,
    // quantity, total, cost}. The cost is the unrounded sum of quantity x the cost copied on each line (POS-05)
    @Query(value = "SELECT to_char(s.created_at AT TIME ZONE 'America/La_Paz', 'YYYY-MM-DD') AS day, s.sale_type, " +
                   "COUNT(*), COALESCE(SUM(i.quantity), 0), SUM(s.total_amount), COALESCE(SUM(i.cost), 0) " +
                   "FROM sales s LEFT JOIN (SELECT sale_id, SUM(quantity) AS quantity, " +
                   "SUM(quantity * unit_cost) AS cost FROM sale_items GROUP BY sale_id) i ON i.sale_id = s.id " +
                   "WHERE s.status = 'COMPLETADA' AND s.created_at >= :from AND s.created_at < :to " +
                   "GROUP BY day, s.sale_type ORDER BY day",
           nativeQuery = true)
    List<Object[]> sumByDayAndType(@Param("from") Instant from, @Param("to") Instant to);

    // Lines of the active sales (FIN-10, CON-03): rows of {sale id, store month 'YYYY-MM', sale type, sale total,
    // supplier id, supplier name, quantity, line subtotal, unrounded cost}. The supplier is the product's current one
    @Query(value = "SELECT s.id, to_char(s.created_at AT TIME ZONE 'America/La_Paz', 'YYYY-MM'), s.sale_type, " +
                   "s.total_amount, sup.id, sup.name, i.quantity, i.subtotal, i.quantity * i.unit_cost " +
                   "FROM sale_items i JOIN sales s ON s.id = i.sale_id JOIN products p ON p.id = i.product_id " +
                   "LEFT JOIN suppliers sup ON sup.id = p.supplier_id " +
                   "WHERE s.status = 'COMPLETADA' AND s.created_at >= :from AND s.created_at < :to",
           nativeQuery = true)
    List<Object[]> findReportLines(@Param("from") Instant from, @Param("to") Instant to);

    // Sale numbers come from a database sequence so concurrent sales never share one (T-06)
    @Query(value = "SELECT nextval('sale_number_seq')", nativeQuery = true)
    long nextSaleNumber();
}
