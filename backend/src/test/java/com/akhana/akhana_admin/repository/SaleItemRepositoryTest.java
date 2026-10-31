package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.SaleItem;
import com.akhana.akhana_admin.model.SaleItemAssignmentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SaleItemRepositoryTest {

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional
    void unitCostStaysFixedWhenProductCostChangesOrSaleIsVoided() {
        UUID[] saleAndProduct = insertSaleAndProduct();
        UUID sale = saleAndProduct[0];
        UUID product = saleAndProduct[1];
        UUID itemId = jdbc.queryForObject(
                "INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, quantity, subtotal) "
                        + "VALUES (?, ?, 'IT producto', 20.00, 12.50, 20.00, 1, 20.00) RETURNING id",
                UUID.class, sale, product);

        jdbc.update("UPDATE products SET buy_price = 14.00 WHERE id = ?", product);
        jdbc.update("UPDATE sales SET status = 'ANULADA', voided_by = 'it-repo', voided_at = now(), void_reason = 'IT' WHERE id = ?", sale);

        // The entity never writes the cost back, even if someone changes it in memory
        SaleItem item = saleItemRepository.findById(itemId).orElseThrow();
        assertEquals(0, new BigDecimal("12.50").compareTo(item.getUnitCost()));
        item.setUnitCost(new BigDecimal("14.00"));
        item.setProductName("IT producto editado");
        entityManager.flush();
        entityManager.clear();

        BigDecimal stored = jdbc.queryForObject("SELECT unit_cost FROM sale_items WHERE id = ?", BigDecimal.class, itemId);
        assertEquals(0, new BigDecimal("12.50").compareTo(stored));
    }

    @Test
    @Transactional
    void assignChangesAPendingLineOnlyOnce() {
        UUID[] saleAndProduct = insertSaleAndProduct();
        UUID itemId = jdbc.queryForObject(
                "INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, quantity, "
                        + "subtotal, free_line_type, free_name, assignment_status) "
                        + "VALUES (?, ?, 'Granola de coco', 25.00, 0, 25.00, 2, 50.00, 'UNIDENTIFIED', 'Granola de coco', "
                        + "'POR_ASIGNAR') RETURNING id",
                UUID.class, saleAndProduct[0], saleAndProduct[1]);
        Product real = entityManager.find(Product.class, saleAndProduct[1]);

        // POS-12: takes the product and its cost; a second assignment matches nothing
        assertEquals(1, saleItemRepository.assign(itemId, real, "GRA-250", "Granola coco 250 g", new BigDecimal("15.50"),
                SaleItemAssignmentStatus.ASIGNADO, "it-repo", Instant.now()));
        assertEquals(0, saleItemRepository.assign(itemId, real, "OTRO", "Otro", BigDecimal.ONE,
                SaleItemAssignmentStatus.SIN_IDENTIFICAR, "it-repo", Instant.now()));

        SaleItem item = saleItemRepository.findById(itemId).orElseThrow();
        assertEquals("Granola coco 250 g", item.getProductName());
        assertEquals("Granola de coco", item.getFreeName());
        assertEquals(0, new BigDecimal("15.50").compareTo(item.getUnitCost()));
        assertEquals(0, new BigDecimal("50.00").compareTo(item.getSubtotal()));
        assertEquals(SaleItemAssignmentStatus.ASIGNADO, item.getAssignmentStatus());
    }

    @Test
    @Transactional
    void quantityKeepsThreeDecimalsAndMustBePositive() {
        UUID[] saleAndProduct = insertSaleAndProduct();
        String insert = "INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, quantity, subtotal) "
                + "VALUES (?, ?, 'IT producto', 20.00, 12.50, 20.00, ?, 7.50) RETURNING id";
        UUID itemId = jdbc.queryForObject(insert, UUID.class, saleAndProduct[0], saleAndProduct[1], new BigDecimal("0.375"));

        SaleItem item = saleItemRepository.findById(itemId).orElseThrow();
        assertEquals(new BigDecimal("0.375"), item.getQuantity());

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.queryForObject(insert, UUID.class, saleAndProduct[0], saleAndProduct[1], BigDecimal.ZERO));
    }

    // Closed session, sale and product rolled back at the end of each test; returns {sale, product}
    private UUID[] insertSaleAndProduct() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT proveedor " + suffix, "ITP-" + suffix);
        UUID category = jdbc.queryForObject(
                "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-repo') RETURNING id",
                UUID.class, "IT categoría " + suffix);
        UUID product = jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, created_by) "
                        + "VALUES (?, ?, ?, ?, 12.50, 20.00, 7.50, 60.00, 'it-repo') RETURNING id",
                UUID.class, "IT-" + suffix, "IT producto " + suffix, category, supplier);
        // Closed so it never collides with a real open session; rolled back at the end
        UUID session = jdbc.queryForObject(
                "INSERT INTO cash_sessions (operation_date, status, opening_amount, opened_by) VALUES (CURRENT_DATE, 'CERRADA', 100, 'it-repo') RETURNING id", UUID.class);
        UUID sale = jdbc.queryForObject(
                "INSERT INTO sales (operation_date, sale_number, cash_session_id, payment_method, total_amount, created_by) "
                        + "VALUES (CURRENT_DATE, ?, ?, 'EFECTIVO', 20.00, 'it-repo') RETURNING id",
                UUID.class, "IT-" + suffix, session);
        return new UUID[] {sale, product};
    }
}
