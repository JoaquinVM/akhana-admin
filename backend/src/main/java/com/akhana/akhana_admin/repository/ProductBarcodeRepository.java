package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, UUID> {

    // CAT-10: at most one, by the partial unique index
    @Query("SELECT b FROM ProductBarcode b JOIN FETCH b.product WHERE b.barcode = :barcode AND b.active = true")
    Optional<ProductBarcode> findActiveByBarcode(@Param("barcode") String barcode);

    /**
     * CAT-10: products still to check, most sold lines first. The count only orders the list: it is not selected, so
     * it cannot reach anyone without access to reports.
     */
    @Query(value = "SELECT p.id AS id, p.code AS code, p.name AS name, s.name AS supplierName, p.sell_price AS sellPrice "
            + "FROM products p "
            + "LEFT JOIN suppliers s ON s.id = p.supplier_id "
            + "LEFT JOIN (SELECT si.product_id, COUNT(*) AS line_count FROM sale_items si "
            + "           JOIN sales sa ON sa.id = si.sale_id "
            + "           WHERE sa.status = 'COMPLETADA' AND sa.operation_date >= :from "
            + "           GROUP BY si.product_id) sold ON sold.product_id = p.id "
            + "WHERE p.status = 'ACTIVO' AND p.sale_unit = 'UNIT' AND p.special_type IS NULL "
            + "AND p.no_barcode_marked_at IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM product_barcodes b WHERE b.product_id = p.id AND b.active) "
            + "ORDER BY COALESCE(sold.line_count, 0) DESC, p.name ASC",
            nativeQuery = true)
    List<PendingRow> findPending(@Param("from") LocalDate from);

    interface PendingRow {
        UUID getId();
        String getCode();
        String getName();
        String getSupplierName();
        BigDecimal getSellPrice();
    }
}
