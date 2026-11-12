package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.CreditorHistoryResponse;
import com.akhana.akhana_admin.service.CreditorService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEU-03 (062): V32 marks what the repayments recorded before V31 paid. Flyway already ran it on this database, so
 * each test seeds its own person with unmarked repayments and runs the SQL again inside the test transaction, which
 * is rolled back at the end.
 */
@SpringBootTest
@Transactional
class CreditorRepaymentItemsMigrationIntegrationTest {

    private static final String BACKFILL = "db/migration/V32__creditor_repayment_items_backfill.sql";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private CreditorService creditorService;
    @Autowired private EntityManager entityManager;

    private String tag;
    private UUID account;
    private UUID supplier;
    private UUID category;
    private UUID product;
    private UUID session;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        account = jdbc.queryForObject("INSERT INTO financial_accounts (name, current_balance) VALUES (?, 1000) RETURNING id",
            UUID.class, "IT Bisa " + tag);
        supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-062') RETURNING id",
            UUID.class, "IT proveedor " + tag, "IT62-" + tag);
        category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-062') "
            + "RETURNING id", UUID.class, "IT cat " + tag);
        session = jdbc.queryForObject("INSERT INTO cash_sessions (operation_date, status, opening_amount, opened_by) "
            + "VALUES ('2001-09-14', 'CERRADA', 0, 'it-062') RETURNING id", UUID.class);
    }

    @Test
    @DisplayName("V32: cada devolución cubre las deudas más antiguas de su concepto, como en producción; "
        + "las anuladas no cuentan y quedan pendientes las ventas que nadie pagó")
    void marksTheOldestDebtsOfEachConcept() {
        UUID mami = creditor("IT Mami " + tag);
        thirdPartyProduct(mami);
        UUID ped487 = payment(mami, "122.00", "2026-11-02", "2026-11-03T02:08:00Z", "ACTIVO");
        UUID ped15 = payment(mami, "630.00", "2026-11-04", "2026-11-05T01:51:00Z", "ACTIVO");
        UUID ped14 = payment(mami, "560.00", "2026-11-04", "2026-11-05T01:52:00Z", "ACTIVO");
        payment(mami, "813.80", "2026-11-05", "2026-11-05T02:00:00Z", "ANULADO");
        UUID his85 = sale(mami, "20.00", "2026-10-20", "2026-10-20T16:00:00Z", "COMPLETADA");
        UUID his86 = sale(mami, "206.00", "2026-10-21", "2026-10-21T16:00:00Z", "COMPLETADA");
        sale(mami, "50.00", "2026-10-22", "2026-10-22T16:00:00Z", "ANULADA");
        UUID vta260 = sale(mami, "30.00", "2026-11-05", "2026-11-05T21:51:00Z", "COMPLETADA");
        UUID thirdParty = repayment(mami, "226.00", "THIRD_PARTY", "2026-11-03", "2026-11-03T20:58:00Z", "ACTIVO");
        UUID first = repayment(mami, "122.00", "PURCHASE", "2026-11-03", "2026-11-03T20:58:29Z", "ACTIVO");
        repayment(mami, "500.00", "PURCHASE", "2026-11-04", "2026-11-04T12:00:00Z", "ANULADO");
        UUID second = repayment(mami, "1190.00", "PURCHASE", "2026-11-05", "2026-11-05T04:43:00Z", "ACTIVO");

        runBackfill();

        assertThat(marks(mami)).containsExactlyInAnyOrder(
            mark(thirdParty, his85, "20.00"), mark(thirdParty, his86, "206.00"),
            mark(first, ped487, "122.00"),
            mark(second, ped15, "630.00"), mark(second, ped14, "560.00"));

        // The history reads them: the debt does not change and only VTA-000260 is pending
        entityManager.clear();
        CreditorHistoryResponse history = creditorService.getHistory(mami);
        assertThat(history.creditor().debt()).isEqualByComparingTo("30.00");
        assertThat(history.entries()).filteredOn(e -> e.pendingAmount() != null && e.pendingAmount().signum() > 0)
            .extracting(CreditorHistoryResponse.Entry::id).containsExactly(vta260);

        // Running it again changes nothing
        runBackfill();
        assertThat(marks(mami)).hasSize(5);
    }

    @Test
    @DisplayName("V32: lo que sobra de una devolución, más que todo lo de su concepto, cubre las deudas más antiguas de otro tipo")
    void leftoverCoversOtherDebts() {
        UUID person = creditor("IT Tía " + tag);
        thirdPartyProduct(person);
        UUID payment = payment(person, "100.00", "2026-10-25", "2026-10-25T15:00:00Z", "ACTIVO");
        UUID oldSale = sale(person, "40.00", "2026-10-20", "2026-10-20T15:00:00Z", "COMPLETADA");
        UUID newSale = sale(person, "50.00", "2026-10-26", "2026-10-26T15:00:00Z", "COMPLETADA");
        // Registered as Pedido for 120 before Pago a tercero existed: 100 pays the purchase, 20 the oldest sale
        UUID repayment = repayment(person, "120.00", "PURCHASE", "2026-10-27", "2026-10-27T15:00:00Z", "ACTIVO");

        runBackfill();

        assertThat(marks(person)).containsExactlyInAnyOrder(
            mark(repayment, payment, "100.00"), mark(repayment, oldSale, "20.00"));
        entityManager.clear();
        CreditorHistoryResponse history = creditorService.getHistory(person);
        assertThat(history.creditor().debt()).isEqualByComparingTo("70.00");
        assertThat(history.entries()).filteredOn(e -> e.id().equals(oldSale)).singleElement()
            .satisfies(e -> assertThat(e.pendingAmount()).isEqualByComparingTo("20.00"));
        assertThat(history.entries()).filteredOn(e -> e.id().equals(newSale)).singleElement()
            .satisfies(e -> assertThat(e.pendingAmount()).isEqualByComparingTo("50.00"));
    }

    private void runBackfill() {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource), new ClassPathResource(BACKFILL));
    }

    private record Mark(UUID repayment, UUID debt, BigDecimal amount) {}

    private static Mark mark(UUID repayment, UUID debt, String amount) {
        return new Mark(repayment, debt, new BigDecimal(amount));
    }

    private List<Mark> marks(UUID creditor) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT ri.repayment_id, "
            + "COALESCE(ri.purchase_payment_id, ri.shipping_charge_id, ri.sale_id) AS debt_id, ri.amount "
            + "FROM creditor_repayment_items ri JOIN creditor_repayments r ON r.id = ri.repayment_id "
            + "WHERE r.creditor_id = ?", creditor);
        return rows.stream().map(r -> new Mark((UUID) r.get("repayment_id"), (UUID) r.get("debt_id"),
            ((BigDecimal) r.get("amount")).setScale(2))).toList();
    }

    private UUID creditor(String name) {
        return jdbc.queryForObject("INSERT INTO creditors (name, status, created_by) VALUES (?, 'ACTIVO', 'it-062') RETURNING id",
            UUID.class, name);
    }

    private void thirdPartyProduct(UUID owner) {
        product = jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, "
            + "fixed_profit, percentage_profit, special_type, owner_creditor_id, created_by) "
            + "VALUES (?, ?, ?, ?, 0, 0, 0, 0, 'THIRD_PARTY', ?, 'it-062') RETURNING id",
            UUID.class, "IT62P-" + tag, "IT de tercero " + tag, category, supplier, owner);
    }

    private UUID payment(UUID creditor, String amount, String day, String at, String status) {
        UUID purchase = jdbc.queryForObject("INSERT INTO purchases (purchase_number, supplier_id, purchase_date, items_total, "
            + "total_amount, created_by) VALUES (?, ?, CAST(? AS DATE), 5000, 5000, 'it-062') RETURNING id",
            UUID.class, "IT62-" + UUID.randomUUID().toString().substring(0, 8), supplier, day);
        return jdbc.queryForObject("INSERT INTO purchase_payments (operation_date, purchase_id, creditor_id, amount, "
            + "payment_date, created_at, status, created_by) VALUES (CAST(? AS DATE), ?, ?, ?, CAST(? AS TIMESTAMPTZ), "
            + "CAST(? AS TIMESTAMPTZ), ?, 'it-062') RETURNING id",
            UUID.class, day, purchase, creditor, new BigDecimal(amount), at, at, status);
    }

    private UUID sale(UUID creditor, String amount, String day, String at, String status) {
        UUID sale = jdbc.queryForObject("INSERT INTO sales (operation_date, sale_number, cash_session_id, payment_method, "
            + "total_amount, status, created_by, created_at) VALUES (CAST(? AS DATE), ?, ?, 'EFECTIVO', ?, ?, 'it-062', "
            + "CAST(? AS TIMESTAMPTZ)) RETURNING id",
            UUID.class, day, "IT62-" + UUID.randomUUID().toString().substring(0, 8), session, new BigDecimal(amount), status, at);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
            + "quantity, subtotal, free_line_type, free_name, creditor_id) "
            + "VALUES (?, ?, 'Incienso', ?, ?, ?, 1, ?, 'THIRD_PARTY', 'Incienso', ?)",
            sale, product, new BigDecimal(amount), new BigDecimal(amount), new BigDecimal(amount), new BigDecimal(amount), creditor);
        return sale;
    }

    private UUID repayment(UUID creditor, String amount, String concept, String day, String at, String status) {
        return jdbc.queryForObject("INSERT INTO creditor_repayments (operation_date, creditor_id, account_id, amount, "
            + "repayment_date, created_at, concept, status, created_by) VALUES (CAST(? AS DATE), ?, ?, ?, "
            + "CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ), ?, ?, 'it-062') RETURNING id",
            UUID.class, day, creditor, account, new BigDecimal(amount), at, at, concept, status);
    }
}
