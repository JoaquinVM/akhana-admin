package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.CashSessionDetailResponse;
import com.akhana.akhana_admin.dto.CashSessionSummaryResponse;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.dto.QuickProductGroupResponse;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.QuickProductService;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;

/**
 * The screens that list many rows read them in a fixed number of queries, whatever the number of rows: the count with
 * few rows equals the count with many. Everything runs in the transaction of the test, rolled back at the end.
 */
@SpringBootTest
@Transactional
class QueryCountIntegrationTest {

    private static final String USER = "it-queries";

    @Autowired private CashSessionService cashSessionService;
    @Autowired private QuickProductService quickProductService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        jdbc.update("DELETE FROM quick_product_groups");
    }

    @AfterEach
    void tearDown() {
        statistics.setStatisticsEnabled(false);
    }

    private <T> long queries(Supplier<T> call) {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        call.get();
        return statistics.getPrepareStatementCount();
    }

    @Test
    @DisplayName("Historial de cajas: una sola consulta cuenta las ventas de todas las cajas")
    void sessionListCountsSalesInOneQuery() {
        CashTestDays.movePastSessionsAway(jdbc);
        LocalDate day = StoreFormat.today(Clock.systemUTC()).minusDays(30);
        UUID product = product("ACTIVO");
        UUID first = CashTestDays.seedClosedSession(jdbc, day, "100.00", USER);
        sale(first, product);
        sale(first, product);
        long few = queries(cashSessionService::getAllSessions);

        for (int i = 1; i <= 6; i++) {
            UUID session = CashTestDays.seedClosedSession(jdbc, day.plusDays(i), "100.00", USER);
            sale(session, product);
        }
        long many = queries(cashSessionService::getAllSessions);

        assertThat(many).isEqualTo(few);
        List<CashSessionSummaryResponse> sessions = cashSessionService.getAllSessions();
        assertThat(sessions).filteredOn(s -> s.id().equals(first)).singleElement()
            .satisfies(s -> assertThat(s.salesCount()).isEqualTo(2));
    }

    @Test
    @DisplayName("Detalle de caja: las líneas de todas las ventas llegan sin una consulta por venta")
    void sessionDetailLoadsItemsInBatches() {
        CashTestDays.movePastSessionsAway(jdbc);
        UUID session = CashTestDays.seedClosedSession(jdbc, StoreFormat.today(Clock.systemUTC()).minusDays(30), "100.00", USER);
        UUID product = product("ACTIVO");
        sale(session, product);
        long few = queries(() -> cashSessionService.getSessionDetail(session));

        for (int i = 0; i < 8; i++) {
            sale(session, product(i % 2 == 0 ? "ACTIVO" : "INACTIVO"));
        }
        long many = queries(() -> cashSessionService.getSessionDetail(session));

        assertThat(many).isEqualTo(few);
        CashSessionDetailResponse detail = cashSessionService.getSessionDetail(session);
        assertThat(detail.sales()).hasSize(9).allSatisfy(sale -> assertThat(sale.items()).hasSize(1));
    }

    @Test
    @DisplayName("Productos rápidos: grupos, productos, categorías, proveedores y etiquetas sin una consulta por producto")
    void quickProductsLoadInFixedQueries() {
        group("Bebidas", 0, product("ACTIVO"));
        long few = queries(quickProductService::getAllGroups);

        group("Snacks", 1, product("ACTIVO"), product("ACTIVO"), product("ACTIVO"));
        group("Dulces", 2, product("ACTIVO"), product("ACTIVO"), product("ACTIVO"), product("ACTIVO"));
        long many = queries(quickProductService::getAllGroups);

        assertThat(many).isEqualTo(few);
        assertThat(quickProductService.getAllGroups()).extracting(g -> g.products().size()).containsExactly(1, 3, 4);
    }

    @Test
    @DisplayName("Productos rápidos: un producto inactivo o eliminado no llega al POS")
    void quickProductsLeaveOutInactiveProducts() {
        UUID active = product("ACTIVO");
        group("Bebidas", 0, product("INACTIVO"), active, product("ELIMINADO"));

        List<QuickProductGroupResponse> groups = quickProductService.getAllGroups();

        assertThat(groups).singleElement().satisfies(g ->
            assertThat(g.products()).extracting(ProductResponse::id).containsExactly(active));
    }

    // --- data ---

    private UUID product(String status) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT categoria " + suffix, USER);
        UUID supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT proveedor " + suffix, "ITQ-" + suffix, USER);
        UUID creditor = jdbc.queryForObject("INSERT INTO creditors (name, created_by) VALUES (?, ?) RETURNING id",
            UUID.class, "IT acreedor " + suffix, USER);
        UUID product = jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, "
                + "fixed_profit, percentage_profit, status, owner_creditor_id, created_by) "
                + "VALUES (?, ?, ?, ?, 5.00, 10.00, 5.00, 0.00, ?, ?, ?) RETURNING id",
            UUID.class, "ITQ-" + suffix, "IT producto " + suffix, category, supplier, status, creditor, USER);
        UUID tag = jdbc.queryForObject("INSERT INTO tags (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT etiqueta " + suffix, USER);
        jdbc.update("INSERT INTO product_tags (product_id, tag_id) VALUES (?, ?)", product, tag);
        return product;
    }

    private void sale(UUID session, UUID product) {
        LocalDate day = jdbc.queryForObject("SELECT operation_date FROM cash_sessions WHERE id = ?", LocalDate.class, session);
        UUID sale = jdbc.queryForObject("INSERT INTO sales (sale_number, cash_session_id, payment_method, total_amount, "
                + "created_by, operation_date) VALUES (?, ?, 'EFECTIVO', 10.00, ?, ?) RETURNING id",
            UUID.class, "ITQ-" + UUID.randomUUID(), session, USER, day);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, final_unit_price, quantity, "
            + "subtotal, unit_cost) VALUES (?, ?, 'IT producto', 10.00, 10.00, 1, 10.00, 5.00)", sale, product);
    }

    private void group(String name, int order, UUID... products) {
        UUID group = jdbc.queryForObject("INSERT INTO quick_product_groups (name, display_order) VALUES (?, ?) RETURNING id",
            UUID.class, name, order);
        for (int i = 0; i < products.length; i++) {
            jdbc.update("INSERT INTO quick_product_group_items (group_id, product_id, display_order) VALUES (?, ?, ?)",
                group, products[i], i);
        }
    }
}
