package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** COM-02 to COM-06 against PostgreSQL: V8 schema, sequence, system categories and checks. */
@SpringBootTest
class PurchaseRepositoryTest {

    @Autowired private PurchaseRepository purchaseRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private FinancialCategoryRepository financialCategoryRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void systemCategoriesPedidoAndTransporteExist() {
        FinancialCategory pedido = financialCategoryRepository
                .findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_PAYMENT).orElseThrow();
        FinancialCategory transporte = financialCategoryRepository
                .findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_SHIPPING).orElseThrow();
        assertEquals(FinancialCategoryType.GASTO, pedido.getType());
        assertEquals(FinancialCategoryType.GASTO, transporte.getType());
    }

    @Test
    void purchaseNumbersComeFromASequence() {
        long first = purchaseRepository.nextPurchaseNumber();
        long second = purchaseRepository.nextPurchaseNumber();
        assertTrue(second > first);
    }

    @Test
    @Transactional
    void purchaseWithLinesRoundTripsAndIsSearchable() {
        UUID[] ids = insertSupplierAndProduct();
        Supplier supplier = supplierRepository.findById(ids[0]).orElseThrow();
        Product product = productRepository.findById(ids[1]).orElseThrow();
        String number = "IT-PED-" + ids[0].toString().substring(0, 8);

        Purchase purchase = Purchase.builder()
                .purchaseNumber(number)
                .supplier(supplier)
                .purchaseDate(LocalDate.now())
                .itemsTotal(new BigDecimal("60.00"))
                .shippingCost(new BigDecimal("5.00"))
                .totalAmount(new BigDecimal("65.00"))
                .paidAmount(new BigDecimal("0.00"))
                .consignment(true)
                .comment("Pedido de prueba")
                .createdBy("it-repo")
                .createdAt(Instant.now())
                .build();
        purchase.getItems().add(PurchaseItem.builder()
                .purchase(purchase)
                .product(product)
                .productName(product.getName())
                .productCode(product.getCode())
                .quantity(new BigDecimal("1.500"))
                .unitCost(new BigDecimal("40.00"))
                .subtotal(new BigDecimal("60.00"))
                .previousUnitCost(new BigDecimal("12.50"))
                .build());
        UUID id = purchaseRepository.save(purchase).getId();
        entityManager.flush();
        entityManager.clear();

        Purchase stored = purchaseRepository.findByIdForUpdate(id).orElseThrow();
        assertEquals(new BigDecimal("1.500"), stored.getItems().get(0).getQuantity());
        assertEquals(PurchasePaymentStatus.CONSIGNACION, stored.getPaymentStatus());

        List<Purchase> bySupplier = purchaseRepository.search(ids[0], null);
        assertEquals(1, bySupplier.size());
        assertEquals(1, purchaseRepository.search(null, number.toLowerCase()).size());
        assertEquals(1, purchaseRepository.search(ids[0], "de prueba").size());
    }

    @Test
    @Transactional
    void purchaseNumberIsUnique() {
        UUID[] ids = insertSupplierAndProduct();
        String number = "IT-PED-" + ids[0].toString().substring(0, 8);
        String insertPurchase = "INSERT INTO purchases (purchase_number, supplier_id, purchase_date, items_total, total_amount, created_by) "
                + "VALUES (?, ?, CURRENT_DATE, 0, 0, 'it-repo') RETURNING id";
        jdbc.queryForObject(insertPurchase, UUID.class, number, ids[0]);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.queryForObject(insertPurchase, UUID.class, number, ids[0]));
    }

    @Test
    @Transactional
    void lineQuantityMustBePositive() {
        UUID[] ids = insertSupplierAndProduct();
        UUID purchase = jdbc.queryForObject(
                "INSERT INTO purchases (purchase_number, supplier_id, purchase_date, items_total, total_amount, created_by) "
                        + "VALUES (?, ?, CURRENT_DATE, 0, 0, 'it-repo') RETURNING id",
                UUID.class, "IT-PED-Q-" + ids[0].toString().substring(0, 8), ids[0]);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO purchase_items (purchase_id, product_id, product_name, quantity, unit_cost, subtotal, previous_unit_cost) "
                        + "VALUES (?, ?, 'IT', 0, 1.00, 0, 1.00)", purchase, ids[1]));
    }

    // Supplier and product rolled back at the end of each test; returns {supplier, product}
    private UUID[] insertSupplierAndProduct() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT proveedor " + suffix, "ITP-" + suffix);
        UUID category = jdbc.queryForObject(
                "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-repo') RETURNING id",
                UUID.class, "IT categoría " + suffix);
        UUID product = jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, sale_unit, created_by) "
                        + "VALUES (?, ?, ?, ?, 12.50, 20.00, 7.50, 60.00, 'KILOGRAM', 'it-repo') RETURNING id",
                UUID.class, "IT-" + suffix, "IT producto " + suffix, category, supplier);
        return new UUID[] {supplier, product};
    }
}
