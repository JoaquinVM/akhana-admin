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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
        assertEquals(0, saleRepository.search(FROM, TO, null, null, null, seller + "-otro", false, PageRequest.of(0, 10)).getTotalElements());

        Page<Sale> secondPage = saleRepository.search(FROM, TO, null, null, null, seller, false,
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

    @Test
    void listsActiveLinesWithTheCurrentSupplierAndStoreMonth() {
        UUID product = insertProduct();
        UUID supplier = jdbc.queryForObject("SELECT supplier_id FROM products WHERE id = ?", UUID.class, product);
        // 23:30 of 31-05 in Bolivia is 03:30 of 01-06 UTC: still May for the store
        UUID sale = insertSale("L1", "EFECTIVO", "NORMAL", "18.00", "0.00", "0.00", null, Instant.parse("2001-06-01T03:30:00Z"));
        insertItem(sale, product, "0.250", "60.00");
        UUID voided = insertSale("LVOID", "EFECTIVO", "NORMAL", "10.00", "0.00", "0.00", null, DAY);
        insertItem(voided, product, "1", "6.00");
        jdbc.update("UPDATE sales SET status = 'ANULADA', voided_by = 'it-repo', voided_at = now(), void_reason = 'IT' WHERE id = ?", voided);
        // The current supplier of the product counts, not the one it had when sold
        UUID newSupplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, code, created_by) VALUES ('IT nuevo', ?, 'it-repo') RETURNING id",
                UUID.class, "ITN-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("UPDATE products SET supplier_id = ? WHERE id = ?", newSupplier, product);

        List<Object[]> lines = saleRepository.findReportLines(Instant.parse("2001-05-01T04:00:00Z"), Instant.parse("2001-06-02T04:00:00Z"));

        assertEquals(1, lines.size());
        Object[] line = lines.getFirst();
        assertEquals(sale, line[0]);
        assertEquals("2001-05", line[1]);
        assertEquals("NORMAL", line[2]);
        assertEquals("18", plain(line[3]));
        assertEquals(newSupplier, line[4]);
        assertEquals("IT nuevo", line[5]);
        assertEquals("0.25", plain(line[6]));
        assertEquals("10", plain(line[7]));
        assertEquals("15", plain(line[8]));
        assertNotEquals(supplier, line[4]);
    }

    @Test
    void countsTheKilosOfALineSoldByPresentation() {
        UUID product = insertProduct();
        UUID presentation = jdbc.queryForObject(
                "INSERT INTO product_presentations (product_id, name, weight_kg, fixed_price, created_by) "
                        + "VALUES (?, '1/2 libra', 0.227, 45.00, 'it-repo') RETURNING id", UUID.class, product);
        // 2 x Nuez pelada 1/2 libra at Bs 45 with cost 130 per kilo: 0.454 kg, cost 59.02 (CAT-11)
        UUID sale = insertSale("P1", "EFECTIVO", "NORMAL", "90.00", "0.00", "0.00", null, DAY);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
                        + "quantity, subtotal, presentation_id, presentation_name, presentation_weight_kg, weight_kg) "
                        + "VALUES (?, ?, 'Nuez pelada', 45.00, 130.00, 45.00, 2, 90.00, ?, '1/2 libra', 0.227, 0.454)",
                sale, product, presentation);

        Object[] day = saleRepository.sumByDayAndType(Instant.parse("2001-05-10T04:00:00Z"), Instant.parse("2001-05-11T04:00:00Z"))
                .stream().filter(row -> "NORMAL".equals(row[1])).findFirst().orElseThrow();
        assertEquals("0.454", plain(day[3]));
        assertEquals("59.02", plain(day[5]));

        Object[] line = saleRepository.findReportLines(Instant.parse("2001-05-10T04:00:00Z"), Instant.parse("2001-05-11T04:00:00Z"))
                .stream().filter(row -> sale.equals(row[0])).findFirst().orElseThrow();
        assertEquals("0.454", plain(line[6]));
        assertEquals("59.02", plain(line[8]));
    }

    @Test
    void separatesPendingAndThirdPartyLinesAndFiltersPendingSales() {
        UUID product = insertProduct();
        UUID mami = jdbc.queryForObject("INSERT INTO creditors (name, created_by) VALUES (?, 'it-repo') RETURNING id",
                UUID.class, "IT Mami " + UUID.randomUUID().toString().substring(0, 8));
        // POS-12, POS-13: one sale of 100 with a normal line of 50 (cost 30), a pending line of 20 and Mami's 30
        UUID sale = insertSale("F1", "EFECTIVO", "NORMAL", "100.00", "0.00", "0.00", null, DAY);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
                + "quantity, subtotal) VALUES (?, ?, 'IT normal', 50.00, 30.00, 50.00, 1, 50.00)", sale, product);
        insertFreeItem(sale, product, "UNIDENTIFIED", "20.00", "0.00", "'POR_ASIGNAR'", null);
        insertFreeItem(sale, product, "THIRD_PARTY", "30.00", "30.00", "NULL", mami);
        // A sale with only Mami's products is not a store sale
        UUID onlyMami = insertSale("F2", "EFECTIVO", "NORMAL", "15.00", "0.00", "0.00", null, DAY);
        insertFreeItem(onlyMami, product, "THIRD_PARTY", "15.00", "15.00", "NULL", mami);

        Object[] day = saleRepository.sumByDayAndType(Instant.parse("2001-05-10T04:00:00Z"), Instant.parse("2001-05-11T04:00:00Z"))
                .stream().filter(row -> "NORMAL".equals(row[1])).findFirst().orElseThrow();
        assertEquals(1, ((Number) day[2]).longValue());
        assertEquals("1", plain(day[3]));
        assertEquals("115", plain(day[4]));
        assertEquals("30", plain(day[5]));
        assertEquals("20", plain(day[6]));
        assertEquals("45", plain(day[7]));

        List<Boolean> normal = saleRepository.findReportLines(Instant.parse("2001-05-10T04:00:00Z"), Instant.parse("2001-05-11T04:00:00Z"))
                .stream().filter(row -> sale.equals(row[0])).map(row -> (Boolean) row[9]).sorted().toList();
        assertEquals(List.of(false, false, true), normal);

        assertEquals(List.of(seller + "-F1"), saleRepository.search(FROM, TO, null, null, null, seller, true,
                PageRequest.of(0, 10)).stream().map(s -> s.getSaleNumber()).toList());
        // Once assigned, the line is normal and the sale is no longer pending
        jdbc.update("UPDATE sale_items SET assignment_status = 'ASIGNADO', unit_cost = 12.00, assigned_by = 'it-repo', "
                + "assigned_at = now() WHERE sale_id = ? AND free_line_type = 'UNIDENTIFIED'", sale);
        assertEquals(0, saleRepository.search(FROM, TO, null, null, null, seller, true, PageRequest.of(0, 10))
                .getTotalElements());
        Object[] assigned = saleRepository.sumByDayAndType(Instant.parse("2001-05-10T04:00:00Z"), Instant.parse("2001-05-11T04:00:00Z"))
                .stream().filter(row -> "NORMAL".equals(row[1])).findFirst().orElseThrow();
        assertEquals("42", plain(assigned[5]));
        assertEquals("0", plain(assigned[6]));
    }

    private void insertFreeItem(UUID sale, UUID product, String type, String price, String cost, String status,
                                UUID creditor) {
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
                        + "quantity, subtotal, free_line_type, free_name, assignment_status, creditor_id) "
                        + "VALUES (?, ?, 'IT libre', ?::numeric, ?::numeric, ?::numeric, 1, ?::numeric, ?, 'IT libre', "
                        + status + ", ?)",
                sale, product, price, cost, price, price, type, creditor);
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
        return saleRepository.search(FROM, TO, status, type, method, seller, false, PageRequest.of(0, 50)).stream()
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
