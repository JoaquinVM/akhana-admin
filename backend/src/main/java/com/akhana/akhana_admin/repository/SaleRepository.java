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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    // A line that counts as a normal sale with its cost: not a free line, or an unidentified one already assigned
    // (POS-12). One closed as unidentified for good stays out of cost and profit
    String NORMAL_LINE = "(free_line_type IS NULL OR COALESCE(assignment_status, '') = 'ASIGNADO')";

    List<Sale> findByCashSessionIdOrderByCreatedAtDesc(UUID cashSessionId);

    long countByCashSessionId(UUID cashSessionId);

    // At-cost sales of a customer, on credit or paid at once (P-49), voided included, for its history (DEU-07),
    // by day of operation (CAJ-11)
    @Query("SELECT s FROM Sale s WHERE s.customer.id = :customerId ORDER BY s.operationDate, s.createdAt")
    List<Sale> findSalesByCustomerId(@Param("customerId") UUID customerId);

    // Sales history (POS-09), by day of operation (CAJ-11), from and to inclusive. A method matches every sale that used it: Efectivo and QR include the mixed ones,
    // Crédito includes the partly-on-credit ones; Mixto matches only mixed sales
    @EntityGraph(attributePaths = {"cashSession", "customer"})
    @Query("SELECT s FROM Sale s " +
           "WHERE s.operationDate BETWEEN :from AND :to " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:saleType IS NULL OR s.saleType = :saleType) " +
           "AND (:paymentMethod IS NULL OR s.paymentMethod = :paymentMethod " +
           "OR (:paymentMethod = com.akhana.akhana_admin.model.PaymentMethod.EFECTIVO AND s.amountCash > 0) " +
           "OR (:paymentMethod = com.akhana.akhana_admin.model.PaymentMethod.QR AND s.amountQr > 0) " +
           "OR (:paymentMethod = com.akhana.akhana_admin.model.PaymentMethod.CREDITO AND s.creditAmount > 0)) " +
           "AND (CAST(:createdBy AS string) IS NULL OR s.createdBy = :createdBy) " +
           // POS-12: completed sales with at least one line waiting for its product
           "AND (:pendingAssignment = false OR (s.status = com.akhana.akhana_admin.model.SaleStatus.COMPLETADA " +
           "AND EXISTS (SELECT 1 FROM SaleItem i WHERE i.sale = s " +
           "AND i.assignmentStatus = com.akhana.akhana_admin.model.SaleItemAssignmentStatus.POR_ASIGNAR)))")
    Page<Sale> search(@Param("from") LocalDate from,
                      @Param("to") LocalDate to,
                      @Param("status") SaleStatus status,
                      @Param("saleType") SaleType saleType,
                      @Param("paymentMethod") PaymentMethod paymentMethod,
                      @Param("createdBy") String createdBy,
                      @Param("pendingAssignment") boolean pendingAssignment,
                      Pageable pageable);

    // Users that registered at least one sale, for the history's user filter (POS-09)
    @Query("SELECT DISTINCT s.createdBy FROM Sale s ORDER BY s.createdBy")
    List<String> findSellers();

    // Active sales per day of operation (CAJ-11) and sale type (FIN-10, CON-03), from and to inclusive: rows of {day 'YYYY-MM-DD', sale type, sales,
    // quantity, total, cost, pending total, third-party total, unidentified total}. The cost is the unrounded sum of
    // quantity x the cost copied on each line (POS-05). A line by presentation counts its kilos, not its packs
    // (CAT-11). Lines waiting for their product or closed as unidentified (POS-12) and third-party lines (POS-13)
    // are left out of quantity and cost and summed apart; a sale with only such lines is not counted. The general
    // discount only applies to the other lines (P-44), so the normal part is the sale total minus those subtotals
    @Query(value = "SELECT to_char(s.operation_date, 'YYYY-MM-DD') AS day, s.sale_type, " +
                   "COUNT(*) FILTER (WHERE COALESCE(i.normal_lines, 1) > 0), COALESCE(SUM(i.quantity), 0), " +
                   "SUM(s.total_amount), COALESCE(SUM(i.cost), 0), COALESCE(SUM(i.pending), 0), " +
                   "COALESCE(SUM(i.third_party), 0), COALESCE(SUM(i.unidentified), 0) " +
                   "FROM sales s LEFT JOIN (SELECT sale_id, " +
                   "SUM(COALESCE(weight_kg, quantity)) FILTER (WHERE " + NORMAL_LINE + ") AS quantity, " +
                   "SUM(COALESCE(weight_kg, quantity) * unit_cost) FILTER (WHERE " + NORMAL_LINE + ") AS cost, " +
                   "SUM(subtotal) FILTER (WHERE assignment_status = 'POR_ASIGNAR') AS pending, " +
                   "SUM(subtotal) FILTER (WHERE free_line_type = 'THIRD_PARTY') AS third_party, " +
                   "SUM(subtotal) FILTER (WHERE assignment_status = 'SIN_IDENTIFICAR') AS unidentified, " +
                   "COUNT(*) FILTER (WHERE " + NORMAL_LINE + ") AS normal_lines " +
                   "FROM sale_items GROUP BY sale_id) i " +
                   "ON i.sale_id = s.id " +
                   "WHERE s.status = 'COMPLETADA' AND s.operation_date BETWEEN :from AND :to " +
                   "GROUP BY day, s.sale_type ORDER BY day",
           nativeQuery = true)
    List<Object[]> sumByDayAndType(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // Lines of the active sales by day of operation (CAJ-11, FIN-10, CON-03): rows of {sale id, month 'YYYY-MM', sale type, sale total,
    // supplier id, supplier name, quantity (kilos for a presentation, CAT-11), line subtotal, unrounded cost,
    // normal line, free line}. The supplier is the product's current one; pending, unidentified and third-party
    // lines (POS-12, POS-13) come with normal line = false. Free lines (POS-11), assigned or not, keep their exact amount: the
    // general discount is spread only over the other lines (P-44)
    @Query(value = "SELECT s.id, to_char(s.operation_date, 'YYYY-MM'), s.sale_type, " +
                   "s.total_amount, sup.id, sup.name, COALESCE(i.weight_kg, i.quantity), i.subtotal, " +
                   "COALESCE(i.weight_kg, i.quantity) * i.unit_cost, " + NORMAL_LINE + ", " +
                   "i.free_line_type IS NOT NULL " +
                   "FROM sale_items i JOIN sales s ON s.id = i.sale_id JOIN products p ON p.id = i.product_id " +
                   "LEFT JOIN suppliers sup ON sup.id = p.supplier_id " +
                   "WHERE s.status = 'COMPLETADA' AND s.operation_date BETWEEN :from AND :to",
           nativeQuery = true)
    List<Object[]> findReportLines(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // Sale numbers come from a database sequence so concurrent sales never share one (T-06)
    @Query(value = "SELECT nextval('sale_number_seq')", nativeQuery = true)
    long nextSaleNumber();

    // Historical sales have their own series and do not use the POS one (FIN-11)
    @Query(value = "SELECT nextval('historical_sale_number_seq')", nativeQuery = true)
    long nextHistoricalSaleNumber();
}
