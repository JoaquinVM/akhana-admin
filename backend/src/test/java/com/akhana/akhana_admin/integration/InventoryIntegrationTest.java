package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.InsufficientStockException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The inventory core (trabajo 105) against PostgreSQL: the valued kardex, the weighted average, the landed cost and the
 * stock moved by sales, consumptions and purchases. Everything runs in the transaction of the test, rolled back.
 */
@SpringBootTest
@Transactional
class InventoryIntegrationTest {

    private static final String USER = "it-inventory";

    @Autowired private CashSessionService cashSessionService;
    @Autowired private SaleService saleService;
    @Autowired private ConsumptionService consumptionService;
    @Autowired private PurchaseService purchaseService;
    @Autowired private ProductService productService;
    @Autowired private InventoryService inventoryService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private String tag;
    private UUID category;
    private UUID supplier;
    private UUID cashAccount;

    @BeforeEach
    void setUp() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assertThat(open).as("La base ya tiene una caja abierta").isZero();
        CashTestDays.movePastSessionsAway(jdbc);
        LocalDate day = StoreFormat.today(Clock.systemUTC()).minusDays(2);
        CashTestDays.seedClosedSession(jdbc, day.minusDays(1), "500.00", USER);
        cashSessionService.openSession(new OpenCashRequest(new BigDecimal("500.00"), "Carga", day, null, null), USER);
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT inv " + tag, USER);
        supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT Natuser " + tag, "ITI-" + tag, USER);
        cashAccount = jdbc.queryForObject("SELECT id FROM financial_accounts WHERE is_cash_account = TRUE", UUID.class);
        next();
    }

    private void next() {
        entityManager.flush();
        entityManager.clear();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private ProductResponse newProduct(String code, String cost, String price, SaleUnit unit,
                                       List<ProductPresentationRequest> packages) {
        ProductResponse product = productService.createProduct(new ProductRequest("IT" + tag + "-" + code, code + " " + tag,
            category, supplier, null, List.of(), new BigDecimal(cost), new BigDecimal(price), ProductStatus.ACTIVO, unit,
            packages, null, null, null), USER);
        next();
        return product;
    }

    private ProductResponse newProduct(String code, String cost, String price) {
        return newProduct(code, cost, price, SaleUnit.UNIT, null);
    }

    // A product of the V1: it waits for its first count (INV-18), as the migration leaves them
    private UUID existingProduct(String code, String cost, String price) {
        UUID id = jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, "
            + "fixed_profit, percentage_profit, created_by) VALUES (?, ?, ?, ?, ?, ?, 0.00, 0.00, ?) RETURNING id",
            UUID.class, "IT" + tag + "-" + code, code + " " + tag, category, supplier, new BigDecimal(cost),
            new BigDecimal(price), USER);
        jdbc.update("INSERT INTO product_stocks (product_id) VALUES (?)", id);
        return id;
    }

    private PurchaseResponse purchase(DeliveryStatus status, String shipping, PurchaseItemRequest... lines) {
        BigDecimal ship = new BigDecimal(shipping);
        PurchaseResponse purchase = purchaseService.registerPurchase(new PurchaseRequest(supplier, null, List.of(lines), ship,
            ship.signum() > 0 ? cashAccount : null, false, null, DeliveryType.EN_TIENDA, status, null, null), USER);
        next();
        return purchase;
    }

    private static PurchaseItemRequest line(UUID product, String quantity, String cost) {
        return new PurchaseItemRequest(product, new BigDecimal(quantity), new BigDecimal(cost), false, null);
    }

    private SaleResponse sell(UUID product, String quantity, boolean allowNegative) {
        SaleResponse sale = saleService.registerSale(new SaleDetailRequest(
            List.of(new SaleItemRequest(product, new BigDecimal(quantity), null)), null, PaymentMethod.QR, null, null, null,
            null, null, null, null, null, allowNegative), USER);
        next();
        return sale;
    }

    // POS-05: the cost the line took, which the responses do not show
    private BigDecimal lineCost(UUID saleId) {
        return jdbc.queryForObject("SELECT unit_cost FROM sale_items WHERE sale_id = ? LIMIT 1", BigDecimal.class, saleId);
    }

    private BigDecimal[] stock(UUID product) {
        return jdbc.queryForObject("SELECT quantity, stock_value, average_cost FROM product_stocks WHERE product_id = ?",
            (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)}, product);
    }

    private List<Map<String, Object>> rows(UUID product) {
        return jdbc.queryForList("SELECT type, quantity, unit_cost, stock_value, expensed_value, balance_quantity, "
            + "balance_value, balance_average_cost, reference, created_by, created_at FROM stock_movements "
            + "WHERE product_id = ? ORDER BY seq", product);
    }

    private void assertLedgerMatches(UUID product) {
        BigDecimal[] s = stock(product);
        BigDecimal qty = jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE product_id = ?",
            BigDecimal.class, product);
        BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(stock_value), 0) FROM stock_movements WHERE product_id = ?",
            BigDecimal.class, product);
        assertThat(s[0]).as("T-04: cantidad = suma del kardex").isEqualByComparingTo(qty);
        assertThat(s[1]).as("T-04: valor = suma del kardex").isEqualByComparingTo(value);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("CAT-05, INV-18: lo nuevo controla stock con 0; lo de la V1 queda por contar y vende al último costo sin avisar; lo marcado no controla")
    void stockControl() {
        ProductResponse fresh = newProduct("NEW", "8.00", "12.00");
        assertThat(fresh.stockStatus()).isEqualTo("CONTROLLED");
        assertThat(fresh.stock()).isEqualByComparingTo("0");

        UUID old = existingProduct("OLD", "5.00", "9.00");
        assertThat(productService.getProductById(old).stockStatus()).isEqualTo("TO_COUNT");
        SaleResponse sale = sell(old, "3", false);
        assertThat(lineCost(sale.id())).isEqualByComparingTo("5.00");
        assertThat(rows(old)).isEmpty();

        ProductResponse off = productService.createProduct(new ProductRequest("IT" + tag + "-OFF", "Off " + tag, category,
            supplier, null, List.of(), new BigDecimal("3.00"), new BigDecimal("5.00"), ProductStatus.ACTIVO, SaleUnit.UNIT,
            null, null, null, null, false), USER);
        next();
        assertThat(off.stockStatus()).isEqualTo("NOT_CONTROLLED");
        sell(off.id(), "2", false);
        assertThat(rows(off.id())).isEmpty();
    }

    @Test
    @DisplayName("INV-06: 10 a Bs 10 + 10 a Bs 12 = 20 a Bs 11; la venta sale a 11, el costo de la línea; T-04: el saldo es la suma del kardex")
    void weightedAverage() {
        ProductResponse tea = newProduct("TEA", "10.00", "15.00");
        purchase(DeliveryStatus.ENTREGADO, "0", line(tea.id(), "10", "10.00"));
        purchase(DeliveryStatus.ENTREGADO, "0", line(tea.id(), "10", "12.00"));
        assertThat(stock(tea.id())[0]).isEqualByComparingTo("20");
        assertThat(stock(tea.id())[2]).isEqualByComparingTo("11");

        SaleResponse sale = sell(tea.id(), "5", false);
        assertThat(lineCost(sale.id())).isEqualByComparingTo("11.00");
        assertThat(stock(tea.id())[0]).isEqualByComparingTo("15");
        assertThat(stock(tea.id())[1]).isEqualByComparingTo("165.00");
        assertLedgerMatches(tea.id());
        assertThat(rows(tea.id())).extracting(r -> r.get("type")).containsExactly("PURCHASE", "PURCHASE", "SALE");
        assertThat(rows(tea.id()).get(2).get("created_by")).isEqualTo(USER);

        // CAT-07: changing the cost by hand changes the last cost only, never the average
        productService.updateProduct(tea.id(), new ProductRequest(tea.code(), tea.name(), category, supplier, null,
            List.of(), new BigDecimal("14.00"), new BigDecimal("18.00"), ProductStatus.ACTIVO, SaleUnit.UNIT), USER);
        next();
        assertThat(stock(tea.id())[2]).isEqualByComparingTo("11");
        assertThat(productService.getProductById(tea.id()).cost()).isEqualByComparingTo("11.00");
    }

    @Test
    @DisplayName("INV-06, INV-10: sin stock pregunta; confirmada sale al último costo y queda en negativo; la entrada fija el promedio")
    void negativeStockAndAverageReset() {
        ProductResponse bar = newProduct("BAR", "8.00", "12.00");
        assertThatThrownBy(() -> sell(bar.id(), "2", false))
            .isInstanceOf(InsufficientStockException.class)
            .hasMessage("No hay stock registrado de " + bar.name() + " (quedan 0). ¿Vender igual?");

        SaleResponse sale = sell(bar.id(), "2", true);
        Boolean marked = jdbc.queryForObject("SELECT negative_stock_confirmed FROM sales WHERE id = ?", Boolean.class, sale.id());
        assertThat(marked).isTrue();
        assertThat(lineCost(sale.id())).isEqualByComparingTo("8.00");
        assertThat(stock(bar.id())[0]).isEqualByComparingTo("-2");
        assertThat(inventoryService.getStock(category).stream().filter(s -> s.productId().equals(bar.id())).findFirst()
            .orElseThrow().negative()).isTrue();

        purchase(DeliveryStatus.ENTREGADO, "0", line(bar.id(), "12", "10.00"));
        List<Map<String, Object>> rows = rows(bar.id());
        assertThat(rows).extracting(r -> r.get("type")).containsExactly("SALE", "AVERAGE_RESET", "PURCHASE");
        assertThat((BigDecimal) rows.get(1).get("stock_value")).isEqualByComparingTo("-4.00");
        assertThat(stock(bar.id())[0]).isEqualByComparingTo("10");
        assertThat(stock(bar.id())[1]).isEqualByComparingTo("100.00");
        assertThat(stock(bar.id())[2]).isEqualByComparingTo("10");
        assertLedgerMatches(bar.id());
    }

    @Test
    @DisplayName("INV-07: el envío se reparte por el valor de cada línea; cambiado después de la entrega va a lo que queda o, sin stock, al costo de ventas")
    void landedCost() {
        ProductResponse a = newProduct("A", "10.00", "15.00");
        ProductResponse b = newProduct("B", "5.00", "8.00");
        PurchaseResponse p = purchase(DeliveryStatus.ENTREGADO, "15.00", line(a.id(), "10", "10.00"), line(b.id(), "10", "5.00"));
        assertThat(stock(a.id())[1]).isEqualByComparingTo("110.00");
        assertThat(stock(b.id())[1]).isEqualByComparingTo("55.00");
        assertThat(stock(a.id())[2]).isEqualByComparingTo("11");

        sell(b.id(), "10", false);
        PurchaseResponse detail = purchaseService.getPurchaseById(p.id());
        purchaseService.updatePurchase(p.id(), new PurchaseUpdateRequest(supplier, detail.purchaseDate(),
            detail.items().stream().map(i -> new PurchaseItemRequest(i.productId(), i.quantity(), i.unitCost(), false, null,
                i.id())).toList(), new BigDecimal("30.00"), cashAccount, false, null, DeliveryType.EN_TIENDA,
            DeliveryStatus.ENTREGADO, detail.deliveryDate(), "Envío real"), USER);
        next();
        assertThat(stock(a.id())[1]).isEqualByComparingTo("120.00");
        List<Map<String, Object>> rowsB = rows(b.id());
        Map<String, Object> last = rowsB.get(rowsB.size() - 1);
        assertThat(last.get("type")).isEqualTo("PURCHASE_CORRECTION");
        assertThat((BigDecimal) last.get("stock_value")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) last.get("expensed_value")).isEqualByComparingTo("5.00");
        assertLedgerMatches(a.id());
        assertLedgerMatches(b.id());
    }

    @Test
    @DisplayName("INV-14, INV-15: Entregado suma; Pendiente o anular resta aunque quede negativo; editar mueve la diferencia; la consignación suma; lo pendiente está por llegar")
    void purchasesMoveTheStock() {
        ProductResponse nut = newProduct("NUT", "10.00", "15.00");
        PurchaseResponse pending = purchase(DeliveryStatus.PENDIENTE, "0", line(nut.id(), "6", "10.00"));
        assertThat(stock(nut.id())[0]).isEqualByComparingTo("0");
        assertThat(inventoryService.getStock(category).stream().filter(s -> s.productId().equals(nut.id())).findFirst()
            .orElseThrow().incoming()).isEqualByComparingTo("6");

        purchaseService.updateDelivery(pending.id(), new PurchaseDeliveryRequest(DeliveryType.EN_TIENDA,
            DeliveryStatus.ENTREGADO, null), USER);
        next();
        assertThat(stock(nut.id())[0]).isEqualByComparingTo("6");
        sell(nut.id(), "4", false);
        purchaseService.updateDelivery(pending.id(), new PurchaseDeliveryRequest(DeliveryType.EN_TIENDA,
            DeliveryStatus.PENDIENTE, null), USER);
        next();
        assertThat(stock(nut.id())[0]).isEqualByComparingTo("-4");

        PurchaseResponse delivered = purchase(DeliveryStatus.ENTREGADO, "0", line(nut.id(), "10", "10.00"));
        assertThat(stock(nut.id())[0]).isEqualByComparingTo("6");
        PurchaseResponse detail = purchaseService.getPurchaseById(delivered.id());
        purchaseService.updatePurchase(delivered.id(), new PurchaseUpdateRequest(supplier, detail.purchaseDate(),
            List.of(new PurchaseItemRequest(nut.id(), new BigDecimal("12"), new BigDecimal("10.00"), false, null,
                detail.items().get(0).id())), BigDecimal.ZERO, null, false, null, DeliveryType.EN_TIENDA,
            DeliveryStatus.ENTREGADO, detail.deliveryDate(), "Llegaron 12"), USER);
        next();
        assertThat(stock(nut.id())[0]).isEqualByComparingTo("8");
        assertThat(rows(nut.id()).get(rows(nut.id()).size() - 1).get("type")).isEqualTo("PURCHASE_CORRECTION");

        purchaseService.voidPurchase(delivered.id(), new VoidPurchaseRequest("Se cargó dos veces"), USER);
        next();
        assertThat(stock(nut.id())[0]).isEqualByComparingTo("-4");

        PurchaseResponse consignment = purchaseService.registerPurchase(new PurchaseRequest(supplier, null,
            List.of(line(nut.id(), "5", "10.00")), BigDecimal.ZERO, null, true, null, DeliveryType.EN_TIENDA,
            DeliveryStatus.ENTREGADO, null, null), USER);
        next();
        assertThat(consignment.consignment()).isTrue();
        assertThat(stock(nut.id())[0]).isEqualByComparingTo("1");
        assertLedgerMatches(nut.id());
    }

    @Test
    @DisplayName("CON-04, INV-09: la caja ×12 resta 24 u, el kilo sus gramos y el consumo al costo; anular devuelve al costo con que salió")
    void salesAndConsumptionsInBaseUnits() {
        ProductResponse bar = newProduct("CHIA", "2.00", "4.00", SaleUnit.UNIT,
            List.of(new ProductPresentationRequest(null, "Caja ×12", new BigDecimal("12"), new BigDecimal("44.00"), true)));
        ProductResponse nuts = newProduct("NUEZ", "100.00", "150.00", SaleUnit.KILOGRAM,
            List.of(new ProductPresentationRequest(null, "1/2 libra", new BigDecimal("0.227"), null, true)));
        purchase(DeliveryStatus.ENTREGADO, "0", line(bar.id(), "48", "2.00"), line(nuts.id(), "2", "100.00"));
        UUID box = bar.presentations().get(0).id();
        UUID half = nuts.presentations().stream().filter(p -> p.name().equals("1/2 libra")).findFirst().orElseThrow().id();

        SaleResponse sale = saleService.registerSale(new SaleDetailRequest(List.of(
            new SaleItemRequest(bar.id(), new BigDecimal("2"), null, box),
            new SaleItemRequest(nuts.id(), BigDecimal.ONE, null, half)), null, PaymentMethod.QR, null, null, null, null,
            null, null, null, null), USER);
        next();
        assertThat(stock(bar.id())[0]).isEqualByComparingTo("24");
        assertThat(stock(nuts.id())[0]).isEqualByComparingTo("1.773");

        ConsumptionResponse consumption = consumptionService.registerConsumption(new ConsumptionRequest(null, "Tienda",
            null, List.of(new ConsumptionItemRequest(nuts.id(), new BigDecimal("0.250")))), USER);
        next();
        assertThat(stock(nuts.id())[0]).isEqualByComparingTo("1.523");

        saleService.voidSale(sale.id(), new VoidSaleRequest("Error"), USER);
        consumptionService.voidConsumption(consumption.id(), new VoidConsumptionRequest("Error"), USER);
        next();
        assertThat(stock(bar.id())[0]).isEqualByComparingTo("48");
        assertThat(stock(bar.id())[1]).isEqualByComparingTo("96.00");
        assertThat(stock(nuts.id())[0]).isEqualByComparingTo("2");
        assertThat(rows(nuts.id())).extracting(r -> r.get("type"))
            .containsExactly("PURCHASE", "SALE", "CONSUMPTION", "SALE_VOID", "CONSUMPTION_VOID");
        assertLedgerMatches(nuts.id());
    }

    @Test
    @DisplayName("INV-12: la línea sin identificar no mueve stock hasta que se asigna y entonces sale al promedio")
    void unidentifiedLines() {
        ProductResponse tea = newProduct("TEU", "10.00", "15.00");
        purchase(DeliveryStatus.ENTREGADO, "0", line(tea.id(), "4", "10.00"));
        List<UUID> found = jdbc.queryForList("SELECT id FROM products WHERE special_type = 'UNIDENTIFIED' "
            + "AND status <> 'ELIMINADO'", UUID.class);
        UUID unidentified = !found.isEmpty() ? found.get(0) : productService.createProduct(new ProductRequest(
            "IT" + tag + "-SIN", "Producto sin identificar " + tag, category, supplier, null, List.of(), BigDecimal.ZERO,
            BigDecimal.ZERO, ProductStatus.ACTIVO, SaleUnit.UNIT, null, ProductSpecialType.UNIDENTIFIED, null), USER).id();
        next();
        SaleResponse sale = saleService.registerSale(new SaleDetailRequest(List.of(new SaleItemRequest(unidentified,
            BigDecimal.ONE, null, null, "Té de algo", new BigDecimal("15.00"))), null, PaymentMethod.QR, null, null, null,
            null, null, null, null, null), USER);
        next();
        assertThat(rows(unidentified)).isEmpty();
        assertThat(stock(tea.id())[0]).isEqualByComparingTo("4");

        saleService.assignItem(sale.id(), sale.items().get(0).id(), tea.id(), USER);
        next();
        assertThat(stock(tea.id())[0]).isEqualByComparingTo("3");
        assertThat(lineCost(sale.id())).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("INV-02, INV-03, INV-08: el ajuste a mano con motivo sale o entra al promedio, con la fecha del servidor; el kardex abre con el saldo anterior")
    void adjustmentsAndKardex() {
        ProductResponse tea = newProduct("TEK", "10.00", "15.00");
        purchase(DeliveryStatus.ENTREGADO, "0", line(tea.id(), "10", "10.00"));
        Instant before = Instant.now();
        inventoryService.adjust(tea.id(), new StockAdjustmentRequest(StockAdjustmentReason.ROTURA, new BigDecimal("2"),
            "Se cayó"), USER);
        KardexResponse kardex = inventoryService.adjust(tea.id(), new StockAdjustmentRequest(StockAdjustmentReason.SOBRANTE,
            BigDecimal.ONE, null), USER);
        next();

        assertThat(kardex.quantity()).isEqualByComparingTo("9");
        assertThat(kardex.value()).isEqualByComparingTo("90.00");
        assertThat(kardex.rows()).extracting(KardexResponse.Row::typeLabel)
            .containsExactly("Pedido entregado", "Ajuste", "Ajuste");
        KardexResponse.Row breakage = kardex.rows().get(1);
        assertThat(breakage.reason()).isEqualTo("Rotura");
        assertThat(breakage.quantity()).isEqualByComparingTo("-2");
        assertThat(breakage.value()).isEqualByComparingTo("-20.00");
        assertThat(breakage.balanceQuantity()).isEqualByComparingTo("8");
        assertThat(breakage.balanceAverageCost()).isEqualByComparingTo("10");
        assertThat(breakage.createdBy()).isEqualTo(USER);
        assertThat(breakage.createdAt()).isAfterOrEqualTo(before.minusSeconds(1));

        LocalDate tomorrow = StoreFormat.today(Clock.systemUTC()).plusDays(1);
        KardexResponse later = inventoryService.getKardex(tea.id(), tomorrow, tomorrow);
        assertThat(later.rows()).isEmpty();
        assertThat(later.openingQuantity()).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("CAT-14: la unidad base no cambia cuando el producto ya movió stock")
    void baseUnitIsKept() {
        ProductResponse tea = newProduct("TEB", "10.00", "15.00");
        purchase(DeliveryStatus.ENTREGADO, "0", line(tea.id(), "1", "10.00"));
        assertThatThrownBy(() -> productService.updateProduct(tea.id(), new ProductRequest(tea.code(), tea.name(), category,
            supplier, null, List.of(), new BigDecimal("10.00"), new BigDecimal("15.00"), ProductStatus.ACTIVO,
            SaleUnit.KILOGRAM), USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ya tiene movimientos de stock");
    }
}
