package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.service.*;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INV-05 and INV-18 against PostgreSQL: a blind count in packages and loose units, the expected stock taken when each
 * quantity is written, and the first count as the initial load. Rolled back at the end of each test.
 */
@SpringBootTest
@Transactional
class StockCountIntegrationTest {

    private static final String USER = "it-count";

    @Autowired private CashSessionService cashSessionService;
    @Autowired private SaleService saleService;
    @Autowired private PurchaseService purchaseService;
    @Autowired private ProductService productService;
    @Autowired private StockCountService countService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private String tag;
    private UUID category;
    private UUID supplier;

    @BeforeEach
    void setUp() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assertThat(open).as("La base ya tiene una caja abierta").isZero();
        CashTestDays.movePastSessionsAway(jdbc);
        LocalDate day = StoreFormat.today(Clock.systemUTC()).minusDays(2);
        CashTestDays.seedClosedSession(jdbc, day.minusDays(1), "100.00", USER);
        cashSessionService.openSession(new OpenCashRequest(new BigDecimal("100.00"), "Carga", day, null, null), USER);
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT conteo " + tag, USER);
        supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT Natuser " + tag, "ITC-" + tag, USER);
        next();
    }

    private void next() {
        entityManager.flush();
        entityManager.clear();
    }

    private ProductResponse barChia() {
        ProductResponse bar = productService.createProduct(new ProductRequest("IT" + tag + "-BAR", "Barra Chia " + tag,
            category, supplier, null, List.of(), new BigDecimal("2.00"), new BigDecimal("4.00"), ProductStatus.ACTIVO,
            SaleUnit.UNIT, List.of(new ProductPresentationRequest(null, "Caja ×12", new BigDecimal("12"), null, true)),
            null, null, null), USER);
        next();
        return bar;
    }

    private UUID existingProduct(String code, String cost) {
        UUID id = jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, "
            + "fixed_profit, percentage_profit, created_by) VALUES (?, ?, ?, ?, ?, 9.00, 0.00, 0.00, ?) RETURNING id",
            UUID.class, "IT" + tag + "-" + code, code + " " + tag, category, supplier, new BigDecimal(cost), USER);
        jdbc.update("INSERT INTO product_stocks (product_id) VALUES (?)", id);
        return id;
    }

    private void sell(UUID product, String quantity) {
        saleService.registerSale(new SaleDetailRequest(List.of(new SaleItemRequest(product, new BigDecimal(quantity), null)),
            null, PaymentMethod.QR, null, null, null, null, null, null, null, null, true), USER);
        next();
    }

    private BigDecimal stock(UUID product) {
        return jdbc.queryForObject("SELECT quantity FROM product_stocks WHERE product_id = ?", BigDecimal.class, product);
    }

    @Test
    @DisplayName("INV-05: «3 Caja ×12 + 4» son 40; a ciegas; lo esperado es el stock al escribir; confirmar ajusta por la diferencia con quién")
    void blindCountInPackages() {
        ProductResponse bar = barChia();
        purchaseService.registerPurchase(new PurchaseRequest(supplier, null, List.of(new PurchaseItemRequest(bar.id(),
            new BigDecimal("37"), new BigDecimal("2.00"), false, null)), BigDecimal.ZERO, null, false, null,
            DeliveryType.EN_TIENDA, DeliveryStatus.ENTREGADO, null, null), USER);
        next();

        StockCountResponse count = countService.create(new StockCountRequest(category), USER);
        assertThat(count.products()).extracting(StockCountResponse.CountProduct::productId).contains(bar.id());
        UUID box = bar.presentations().get(0).id();
        StockCountResponse saved = countService.saveLine(count.id(), bar.id(), new StockCountLineRequest(
            List.of(new StockCountLineRequest.PackageCount(box, new BigDecimal("3"))), new BigDecimal("4")), USER);
        next();
        StockCountResponse.CountProduct line = saved.products().stream().filter(p -> p.productId().equals(bar.id()))
            .findFirst().orElseThrow();
        assertThat(line.countedQuantity()).isEqualByComparingTo("40");
        assertThat(line.breakdown()).isEqualTo("3 Caja ×12 + 4");
        // What was saved comes back by package, so counting again adds to it
        assertThat(line.countedPackages().get(box)).isEqualByComparingTo("3");
        assertThat(line.countedLoose()).isEqualByComparingTo("4");

        // Sold after the count was written: it leaves the stock and the shelf, and does not change the difference
        sell(bar.id(), "2");
        StockCountReviewResponse review = countService.review(count.id());
        StockCountReviewResponse.Line reviewed = review.lines().get(0);
        assertThat(reviewed.expectedQuantity()).isEqualByComparingTo("37");
        assertThat(reviewed.difference()).isEqualByComparingTo("3");
        assertThat(reviewed.differenceValue()).isEqualByComparingTo("6.00");

        countService.confirm(count.id(), USER);
        next();
        assertThat(stock(bar.id())).isEqualByComparingTo("38");
        assertThat(jdbc.queryForObject("SELECT created_by FROM stock_movements WHERE product_id = ? AND type = 'COUNT_ADJUSTMENT'",
            String.class, bar.id())).isEqualTo(USER);
        assertThatThrownBy(() -> countService.saveLine(count.id(), bar.id(), new StockCountLineRequest(List.of(),
            BigDecimal.ONE), USER)).isInstanceOf(IllegalStateException.class).hasMessage("El conteo ya está cerrado.");
    }

    @Test
    @DisplayName("INV-18: el primer conteo es la carga inicial al último costo, menos lo vendido después de escribirlo; desde ahí controla stock")
    void firstCountIsTheInitialLoad() {
        UUID oil = existingProduct("ACE", "5.00");
        StockCountResponse count = countService.create(new StockCountRequest(category), USER);
        assertThat(count.products()).filteredOn(p -> p.productId().equals(oil)).singleElement()
            .satisfies(p -> assertThat(p.status()).isEqualTo("TO_COUNT"));
        countService.saveLine(count.id(), oil, new StockCountLineRequest(List.of(), new BigDecimal("10")), USER);
        next();

        // Still to count: no warning, at the last cost and without stock movements
        sell(oil, "1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id = ?", Integer.class, oil)).isZero();

        // The same product cannot be in two open counts
        StockCountResponse other = countService.create(new StockCountRequest(null), USER);
        assertThatThrownBy(() -> countService.saveLine(other.id(), oil, new StockCountLineRequest(List.of(), BigDecimal.ONE), USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ya está en el Conteo #" + count.number());

        StockCountReviewResponse.Line load = countService.review(count.id()).lines().get(0);
        assertThat(load.initialLoad()).isTrue();
        assertThat(load.difference()).isEqualByComparingTo("9");
        assertThat(load.differenceValue()).isEqualByComparingTo("45.00");

        countService.confirm(count.id(), USER);
        next();
        assertThat(stock(oil)).isEqualByComparingTo("9");
        assertThat(jdbc.queryForObject("SELECT average_cost FROM product_stocks WHERE product_id = ?", BigDecimal.class, oil))
            .isEqualByComparingTo("5");
        assertThat(productService.getProductById(oil).stockStatus()).isEqualTo("CONTROLLED");
        assertThat(jdbc.queryForObject("SELECT type FROM stock_movements WHERE product_id = ?", String.class, oil))
            .isEqualTo("INITIAL_LOAD");
    }

    @Test
    @DisplayName("INV-18: lo entregado con el primer conteo abierto entra con la carga inicial y después se mueve como cualquier pedido")
    void deliveredDuringTheFirstCount() {
        UUID chia = existingProduct("CHIA", "2.00");
        StockCountResponse count = countService.create(new StockCountRequest(null), USER);
        countService.saveLine(count.id(), chia, new StockCountLineRequest(List.of(), new BigDecimal("27")), USER);
        next();

        // The box arrives while the count is open: the product is still to count, so it does not move yet
        PurchaseResponse box = purchaseService.registerPurchase(new PurchaseRequest(supplier, null, List.of(
            new PurchaseItemRequest(chia, new BigDecimal("12"), new BigDecimal("2.10"), false, null)), BigDecimal.ZERO,
            null, false, null, DeliveryType.EN_TIENDA, DeliveryStatus.PENDIENTE, null, null), USER);
        purchaseService.updateDelivery(box.id(), new PurchaseDeliveryRequest(DeliveryType.EN_TIENDA,
            DeliveryStatus.ENTREGADO, null), USER);
        next();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id = ?", Integer.class, chia)).isZero();

        StockCountReviewResponse.Line load = countService.review(count.id()).lines().get(0);
        assertThat(load.difference()).isEqualByComparingTo("39");
        assertThat(load.differenceValue()).isEqualByComparingTo("79.20");

        countService.confirm(count.id(), USER);
        next();
        assertThat(stock(chia)).isEqualByComparingTo("39");
        assertThat(jdbc.queryForList("SELECT type FROM stock_movements WHERE product_id = ? ORDER BY seq", String.class, chia))
            .containsExactly("INITIAL_LOAD", "PURCHASE");

        // Linked to its line, it goes out if the purchase goes back to pending
        purchaseService.updateDelivery(box.id(), new PurchaseDeliveryRequest(DeliveryType.EN_TIENDA,
            DeliveryStatus.PENDIENTE, null), USER);
        next();
        assertThat(stock(chia)).isEqualByComparingTo("27");
    }
}
