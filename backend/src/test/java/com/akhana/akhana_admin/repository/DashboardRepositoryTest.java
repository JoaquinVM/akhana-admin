package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.service.impl.StoreFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Payments and product lines of the dashboard (DSH-02, DSH-03); every row is rolled back at the end of each test
@SpringBootTest
@Transactional
class DashboardRepositoryTest {

    // 15-06-2001 at 11:00 in La Paz, and that store day
    private static final Instant DAY = Instant.parse("2001-06-15T15:00:00Z");
    private static final LocalDate DAY_OF_OPERATION = LocalDate.of(2001, 6, 15);
    private static final Instant TO = Instant.parse("2001-06-16T04:00:00Z");

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String suffix;
    private UUID session;
    private UUID product;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        session = jdbc.queryForObject(
                "INSERT INTO cash_sessions (status, opening_amount, opened_by, operation_date) VALUES ('CERRADA', 100, 'it-repo', '2001-06-15') RETURNING id", UUID.class);
        UUID category = jdbc.queryForObject(
                "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-repo') RETURNING id",
                UUID.class, "IT categoría " + suffix);
        UUID supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT proveedor " + suffix, "ITD-" + suffix);
        product = jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, created_by) "
                        + "VALUES (?, ?, ?, ?, 6.00, 10.00, 4.00, 66.67, 'it-repo') RETURNING id",
                UUID.class, "ITD-" + suffix, "IT producto " + suffix, category, supplier);
    }

    @Test
    void paymentsLeaveOutVoidedSales() {
        insertSale("C", "EFECTIVO", "NORMAL", "50.00", "0.00", "0.00", DAY);
        insertSale("M", "MIXTO", "NORMAL", "10.00", "20.00", "0.00", DAY);
        insertSale("AC", "CREDITO", "AL_COSTO", "0.00", "0.00", "15.00", DAY);
        UUID voided = insertSale("V", "EFECTIVO", "NORMAL", "99.00", "0.00", "0.00", DAY);
        jdbc.update("UPDATE sales SET status = 'ANULADA', voided_by = 'it-repo', voided_at = now(), void_reason = 'IT' WHERE id = ?", voided);
        // A spreadsheet day (FIN-11, P-32): one historical sale without how it was paid
        UUID historicalImport = jdbc.queryForObject(
                "INSERT INTO historical_imports (cutoff_date, imported_by) VALUES ('2001-06-30', 'it-repo') RETURNING id",
                UUID.class);
        jdbc.update("INSERT INTO sales (sale_number, payment_method, sale_type, total_amount, subtotal_amount, "
                        + "historical_import_id, created_by, created_at, operation_date) "
                        + "VALUES (?, 'HISTORICO', 'NORMAL', 300.00, 300.00, ?, 'it-dash', ?, ?)",
                "ITD-" + suffix + "-H", historicalImport, Timestamp.from(DAY), DAY_OF_OPERATION);
        // The day after, outside the range
        insertSale("N", "QR", "NORMAL", "0.00", "7.00", "0.00", TO.plusSeconds(60));

        Object[] row = dashboardRepository.sumPayments(DAY_OF_OPERATION, DAY_OF_OPERATION).getFirst();

        assertEquals("60", plain(row[0]));
        assertEquals("20", plain(row[1]));
        assertEquals("15", plain(row[2]));
        assertEquals("300", plain(row[3]));
        assertEquals(1L, ((Number) row[4]).longValue());
    }

    @Test
    void productLinesOnlyOfActiveNormalSales() {
        UUID normal = insertSale("N1", "EFECTIVO", "NORMAL", "20.00", "0.00", "0.00", DAY);
        insertItem(normal, "2", "20.00", "6.00");
        UUID atCost = insertSale("AC1", "EFECTIVO", "AL_COSTO", "6.00", "0.00", "0.00", DAY);
        insertItem(atCost, "1", "6.00", "6.00");
        UUID voided = insertSale("V1", "EFECTIVO", "NORMAL", "10.00", "0.00", "0.00", DAY);
        insertItem(voided, "1", "10.00", "6.00");
        jdbc.update("UPDATE sales SET status = 'ANULADA', voided_by = 'it-repo', voided_at = now(), void_reason = 'IT' WHERE id = ?", voided);

        List<Object[]> lines = dashboardRepository.findProductLines(DAY_OF_OPERATION, DAY_OF_OPERATION);

        assertEquals(1, lines.size());
        Object[] line = lines.getFirst();
        assertEquals(normal, line[0]);
        assertEquals(product, line[2]);
        assertEquals("IT producto " + suffix, line[3]);
        assertEquals("2", plain(line[4]));
        assertEquals("20", plain(line[5]));
        assertEquals("12", plain(line[6]));
        assertEquals(true, line[7]);
        assertEquals(false, line[8]);
    }

    @Test
    void countsSalesOnTheDayOfOperation() {
        // CAJ-11: recorded after midnight in the session of 15-06, it counts for 15-06 and not for 16-06
        UUID late = insertSale("L", "QR", "NORMAL", "0.00", "12.00", "0.00", TO.plusSeconds(1200), DAY_OF_OPERATION);
        insertItem(late, "1", "12.00", "6.00");

        assertEquals("12", plain(dashboardRepository.sumPayments(DAY_OF_OPERATION, DAY_OF_OPERATION).getFirst()[1]));
        assertEquals(List.of(late), dashboardRepository.findProductLines(DAY_OF_OPERATION, DAY_OF_OPERATION).stream()
                .map(line -> line[0]).toList());
        LocalDate next = DAY_OF_OPERATION.plusDays(1);
        assertEquals("0", plain(dashboardRepository.sumPayments(next, next).getFirst()[1]));
        assertEquals(0, dashboardRepository.findProductLines(next, next).size());
    }

    private static String plain(Object value) {
        return ((BigDecimal) value).stripTrailingZeros().toPlainString();
    }

    private void insertItem(UUID sale, String quantity, String subtotal, String unitCost) {
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, quantity, subtotal) "
                        + "VALUES (?, ?, 'IT producto', 10.00, ?::numeric, 10.00, ?::numeric, ?::numeric)",
                sale, product, unitCost, quantity, subtotal);
    }

    // Recorded on the store day it counts for, as every record before CAJ-11
    private UUID insertSale(String number, String method, String type, String cash, String qr, String credit,
                            Instant createdAt) {
        return insertSale(number, method, type, cash, qr, credit, createdAt,
                createdAt.atZone(StoreFormat.STORE_ZONE).toLocalDate());
    }

    private UUID insertSale(String number, String method, String type, String cash, String qr, String credit,
                            Instant createdAt, LocalDate operationDate) {
        String total = new BigDecimal(cash).add(new BigDecimal(qr)).add(new BigDecimal(credit)).toPlainString();
        UUID customer = new BigDecimal(credit).signum() > 0
                ? jdbc.queryForObject("INSERT INTO customers (name, created_by) VALUES (?, 'it-repo') RETURNING id",
                        UUID.class, "IT cliente " + suffix + number)
                : null;
        return jdbc.queryForObject(
                "INSERT INTO sales (sale_number, cash_session_id, payment_method, sale_type, total_amount, subtotal_amount, "
                        + "amount_cash, amount_qr, credit_amount, customer_id, created_by, created_at, operation_date) "
                        + "VALUES (?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?, 'it-dash', ?, ?) RETURNING id",
                UUID.class, "ITD-" + suffix + "-" + number, session, method, type, total, total, cash, qr, credit,
                customer, Timestamp.from(createdAt), operationDate);
    }
}
