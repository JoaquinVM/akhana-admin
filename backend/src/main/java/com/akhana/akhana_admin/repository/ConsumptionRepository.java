package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Consumption;
import com.akhana.akhana_admin.model.ConsumptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumptionRepository extends JpaRepository<Consumption, UUID> {

    // Consumption numbers come from a database sequence, like sales and purchases (T-06)
    @Query(value = "SELECT nextval('consumption_number_seq')", nativeQuery = true)
    long nextConsumptionNumber();

    // A void locks its consumption so two voids do not overlap
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Consumption c WHERE c.id = :id")
    Optional<Consumption> findByIdForUpdate(@Param("id") UUID id);

    // Text matches the number, who consumed it, the comment or a line's product name (CON-01)
    @Query("SELECT c FROM Consumption c " +
           "WHERE (CAST(:from AS LocalDate) IS NULL OR c.consumptionDate >= :from) " +
           "AND (CAST(:to AS LocalDate) IS NULL OR c.consumptionDate <= :to) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:productId IS NULL OR EXISTS (SELECT 1 FROM ConsumptionItem i " +
           "WHERE i.consumption = c AND i.product.id = :productId)) " +
           "AND (CAST(:search AS string) IS NULL " +
           "OR LOWER(c.consumptionNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR (c.consumedFor IS NOT NULL AND LOWER(c.consumedFor) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "OR (c.comment IS NOT NULL AND LOWER(c.comment) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "OR EXISTS (SELECT 1 FROM ConsumptionItem i2 WHERE i2.consumption = c " +
           "AND LOWER(i2.productName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))) " +
           "ORDER BY c.consumptionDate DESC, c.createdAt DESC")
    List<Consumption> search(@Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("status") ConsumptionStatus status,
                             @Param("productId") UUID productId,
                             @Param("search") String search);

    // Active consumptions per month (FIN-10, CON-03): rows of {month 'YYYY-MM', consumptions, total at cost}
    @Query(value = "SELECT to_char(consumption_date, 'YYYY-MM') AS month, COUNT(*), SUM(total_amount) " +
                   "FROM consumptions WHERE status = 'REGISTRADO' AND consumption_date BETWEEN :from AND :to " +
                   "GROUP BY month",
           nativeQuery = true)
    List<Object[]> sumByMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
