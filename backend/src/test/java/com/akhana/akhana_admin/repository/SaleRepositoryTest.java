package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.Sale;
import com.akhana.akhana_admin.model.SaleStatus;
import com.akhana.akhana_admin.model.SaleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Sales history filters (POS-09); every row is rolled back at the end of each test
@SpringBootTest
@Transactional
class SaleRepositoryTest {

    private static final Instant DAY = Instant.parse("2001-05-10T15:00:00Z");
    private static final Instant FROM = Instant.parse("2001-05-10T00:00:00Z");
    private static final Instant TO = Instant.parse("2001-05-11T00:00:00Z");

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String seller;
    private UUID session;
    private UUID customer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        seller = "it-sales-" + suffix;
        session = jdbc.queryForObject(
                "INSERT INTO cash_sessions (status, opening_amount, opened_by) VALUES ('CERRADA', 100, 'it-repo') RETURNING id", UUID.class);
        customer = jdbc.queryForObject(
                "INSERT INTO customers (name, created_by) VALUES (?, 'it-repo') RETURNING id", UUID.class, "IT cliente " + suffix);
    }

    @Test
    void paymentMethodMatchesEverySaleThatUsedIt() {
        insertSale("CASH", "EFECTIVO", "NORMAL", "50.00", "0.00", "0.00", null, DAY);
        insertSale("QR", "QR", "NORMAL", "0.00", "30.00", "0.00", null, DAY);
        insertSale("MIX", "MIXTO", "NORMAL", "10.00", "20.00", "0.00", null, DAY);
        insertSale("PART", "EFECTIVO", "AL_COSTO", "5.00", "0.00", "15.00", customer, DAY);
        insertSale("CRED", "CREDITO", "AL_COSTO", "0.00", "0.00", "40.00", customer, DAY);

        assertEquals(List.of("CASH", "MIX", "PART"), numbers(PaymentMethod.EFECTIVO, null, null));
        assertEquals(List.of("MIX", "QR"), numbers(PaymentMethod.QR, null, null));
        assertEquals(List.of("MIX"), numbers(PaymentMethod.MIXTO, null, null));
        assertEquals(List.of("CRED", "PART"), numbers(PaymentMethod.CREDITO, null, null));
        assertEquals(List.of("CRED", "PART"), numbers(null, SaleType.AL_COSTO, null));
        assertEquals(5, numbers(null, null, null).size());
    }

    @Test
    void filtersByDateStatusAndUserAndPaginatesNewestFirst() {
        insertSale("OLD", "EFECTIVO", "NORMAL", "10.00", "0.00", "0.00", null, FROM.minusSeconds(1));
        insertSale("FIRST", "EFECTIVO", "NORMAL", "10.00", "0.00", "0.00", null, DAY);
        insertSale("LAST", "EFECTIVO", "NORMAL", "10.00", "0.00", "0.00", null, DAY.plusSeconds(60));
        UUID voided = insertSale("VOID", "EFECTIVO", "NORMAL", "10.00", "0.00", "0.00", null, DAY.plusSeconds(30));
        jdbc.update("UPDATE sales SET status = 'ANULADA', voided_by = 'it-repo', voided_at = now(), void_reason = 'IT' WHERE id = ?", voided);

        assertEquals(List.of("VOID"), numbers(null, null, SaleStatus.ANULADA));
        assertEquals(List.of("FIRST", "LAST"), numbers(null, null, SaleStatus.COMPLETADA));
        assertEquals(0, saleRepository.search(FROM, TO, null, null, null, seller + "-otro", PageRequest.of(0, 10)).getTotalElements());

        Page<Sale> secondPage = saleRepository.search(FROM, TO, null, null, null, seller,
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertEquals(3, secondPage.getTotalElements());
        assertEquals(2, secondPage.getTotalPages());
        assertEquals(List.of(seller + "-FIRST"), secondPage.map(Sale::getSaleNumber).getContent());
    }

    @Test
    void sumsActiveSalesPerStoreDayAndType() {
        UUID product = insertProduct();
        UUID normal = insertSale("N1", "EFECTIVO", "NORMAL", "35.00", "0.00", "0.00", null, DAY);
        insertItem(normal, product, "2", "6.00");
        insertItem(normal, product, "0.250", "60.00");
        UUID atCost = insertSale("C1", "CREDITO", "AL_COSTO", "0.00", "0.00", "104.00", customer, DAY);
        insertItem(atCost, product, "1", "104.00");
        // 23:30 of 10-05 in Bolivia is 03:30 of 11-05 UTC: still the 10th for the store
        UUID late = insertSale("N2", "QR", "NORMAL", "0.00", "10.00", "0.00", null, Instant.parse("2001-05-11T03:30:00Z"));
        insertItem(late, product, "1", "4.00");
        UUID nextDay = insertSale("N3", "EFECTIVO", "NORMAL", "8.00", "0.00", "0.00", null, Instant.parse("2001-05-11T05:00:00Z"));
        insertItem(nextDay, product, "1", "5.00");
        UUID voided = insertSale("VOID", "EFECTIVO", "NORMAL", "99.00", "0.00", "0.00", null, DAY);
        insertItem(voided, product, "1", "50.00");
        jdbc.update("UPDATE sales SET status = 'ANULADA', voided_by = 'it-repo', voided_at = now(), void_reason = 'IT' WHERE id = ?", voided);

        List<String> rows = saleRepository.sumByDayAndType(Instant.parse("2001-05-10T04:00:00Z"), Instant.parse("2001-05-12T04:00:00Z"))
                .stream()
                .map(row -> row[0] + " " + row[1] + " " + ((Number) row[2]).longValue() + " " + plain(row[3]) + " " + plain(row[4]) + " " + plain(row[5]))
                .sorted()
                .toList();

        assertEquals(List.of(
                "2001-05-10 AL_COSTO 1 1 104 104",
                "2001-05-10 NORMAL 2 3.25 45 31",
                "2001-05-11 NORMAL 1 1 8 5"), rows);
    }

    private static String plain(Object value) {
        return ((BigDecimal) value).stripTrailingZeros().toPlainString();
    }

    private UUID insertProduct() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID category = jdbc.queryForObject(
                "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-repo') RETURNING id",
                UUID.class, "IT categoría " + suffix);
        UUID supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT proveedor " + suffix, "ITP-" + suffix);
        return jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, created_by) "
                        + "VALUES (?, ?, ?, ?, 6.00, 10.00, 4.00, 66.67, 'it-repo') RETURNING id",
                UUID.class, "IT-" + suffix, "IT producto " + suffix, category, supplier);
    }

    private void insertItem(UUID sale, UUID product, String quantity, String unitCost) {
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, quantity, subtotal) "
                        + "VALUES (?, ?, 'IT producto', 10.00, ?::numeric, 10.00, ?::numeric, 10.00)",
                sale, product, unitCost, quantity);
    }

    // Sorted by number so each assertion reads the same whatever the creation order
    private List<String> numbers(PaymentMethod method, SaleType type, SaleStatus status) {
        return saleRepository.search(FROM, TO, status, type, method, seller, PageRequest.of(0, 50)).stream()
                .map(sale -> sale.getSaleNumber().substring(seller.length() + 1))
                .sorted()
                .toList();
    }

    private UUID insertSale(String number, String method, String type, String cash, String qr, String credit,
                            UUID customerId, Instant createdAt) {
        String total = new BigDecimal(cash).add(new BigDecimal(qr)).add(new BigDecimal(credit)).toPlainString();
        return jdbc.queryForObject(
                "INSERT INTO sales (sale_number, cash_session_id, payment_method, sale_type, total_amount, subtotal_amount, "
                        + "amount_cash, amount_qr, credit_amount, customer_id, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?::numeric, ?, ?, ?) RETURNING id",
                UUID.class, seller + "-" + number, session, method, type, total, total, cash, qr, credit, customerId, seller,
                Timestamp.from(createdAt));
    }
}
