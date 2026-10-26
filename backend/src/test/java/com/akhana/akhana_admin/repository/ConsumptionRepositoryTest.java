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

/** CON-01 against PostgreSQL: V11 schema, sequence, checks and the list filters. */
@SpringBootTest
class ConsumptionRepositoryTest {

    private static final String INSERT_CONSUMPTION =
            "INSERT INTO consumptions (consumption_number, consumption_date, total_amount, status, created_by) "
                    + "VALUES (?, CURRENT_DATE, 0, ?, 'it-repo') RETURNING id";

    @Autowired private ConsumptionRepository consumptionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void consumptionNumbersComeFromASequence() {
        long first = consumptionRepository.nextConsumptionNumber();
        long second = consumptionRepository.nextConsumptionNumber();
        assertTrue(second > first);
    }

    @Test
    @Transactional
    void consumptionWithLinesRoundTripsAndIsFilterable() {
        UUID productId = insertProduct();
        Product product = productRepository.findById(productId).orElseThrow();
        String number = "IT-CON-" + productId.toString().substring(0, 8);
        LocalDate day = LocalDate.of(2020, 1, 15);

        Consumption consumption = Consumption.builder()
                .consumptionNumber(number)
                .consumptionDate(day)
                .consumedFor("Ali de prueba")
                .comment("Regalo")
                .totalAmount(new BigDecimal("6.00"))
                .createdBy("it-repo")
                .createdAt(Instant.now())
                .build();
        consumption.getItems().add(ConsumptionItem.builder()
                .consumption(consumption)
                .product(product)
                .productName(product.getName())
                .productCode(product.getCode())
                .quantity(new BigDecimal("0.100"))
                .unitCost(new BigDecimal("60.00"))
                .subtotal(new BigDecimal("6.00"))
                .build());
        UUID id = consumptionRepository.save(consumption).getId();
        entityManager.flush();
        entityManager.clear();

        Consumption stored = consumptionRepository.findByIdForUpdate(id).orElseThrow();
        assertEquals(new BigDecimal("0.100"), stored.getItems().get(0).getQuantity());
        assertEquals(ConsumptionStatus.REGISTRADO, stored.getStatus());

        assertEquals(List.of(id), ids(consumptionRepository.search(day, day, null, null, null)));
        assertEquals(List.of(id), ids(consumptionRepository.search(null, null, ConsumptionStatus.REGISTRADO, productId, null)));
        assertEquals(List.of(id), ids(consumptionRepository.search(null, null, null, null, number.toLowerCase())));
        assertEquals(List.of(id), ids(consumptionRepository.search(null, null, null, null, "ali de prueba")));
        assertEquals(List.of(id), ids(consumptionRepository.search(null, null, null, null, product.getName().toUpperCase())));
        assertTrue(consumptionRepository.search(day.plusDays(1), null, null, productId, null).isEmpty());
        assertTrue(consumptionRepository.search(null, null, ConsumptionStatus.ANULADO, productId, null).isEmpty());
        assertTrue(consumptionRepository.search(null, null, null, UUID.randomUUID(), number).isEmpty());
    }

    @Test
    @Transactional
    void consumptionNumberIsUnique() {
        String number = "IT-CON-U-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.queryForObject(INSERT_CONSUMPTION, UUID.class, number, "REGISTRADO");

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.queryForObject(INSERT_CONSUMPTION, UUID.class, number, "REGISTRADO"));
    }

    @Test
    @Transactional
    void statusIsChecked() {
        String number = "IT-CON-S-" + UUID.randomUUID().toString().substring(0, 8);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.queryForObject(INSERT_CONSUMPTION, UUID.class, number, "BORRADO"));
    }

    @Test
    @Transactional
    void lineQuantityMustBePositive() {
        UUID productId = insertProduct();
        UUID consumption = jdbc.queryForObject(
                "INSERT INTO consumptions (consumption_number, consumption_date, total_amount, created_by) "
                        + "VALUES (?, CURRENT_DATE, 0, 'it-repo') RETURNING id",
                UUID.class, "IT-CON-Q-" + productId.toString().substring(0, 8));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO consumption_items (consumption_id, product_id, product_name, quantity, unit_cost, subtotal) "
                        + "VALUES (?, ?, 'IT', 0, 1.00, 0)", consumption, productId));
    }

    private static List<UUID> ids(List<Consumption> consumptions) {
        return consumptions.stream().map(Consumption::getId).toList();
    }

    // Supplier, category and product rolled back at the end of each test
    private UUID insertProduct() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT proveedor " + suffix, "ITP-" + suffix);
        UUID category = jdbc.queryForObject(
                "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-repo') RETURNING id",
                UUID.class, "IT categoría " + suffix);
        return jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, sale_unit, created_by) "
                        + "VALUES (?, ?, ?, ?, 60.00, 90.00, 30.00, 50.00, 'KILOGRAM', 'it-repo') RETURNING id",
                UUID.class, "IT-" + suffix, "IT producto " + suffix, category, supplier);
    }
}
