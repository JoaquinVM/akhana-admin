package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.dto.BarcodePendingResponse;
import com.akhana.akhana_admin.service.ProductBarcodeService;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// CAT-10: the «Cargar códigos» list against PostgreSQL; every row is rolled back at the end of each test
@SpringBootTest
@Transactional
class ProductBarcodeRepositoryTest {

    @Autowired private ProductBarcodeService service;
    @Autowired private JdbcTemplate jdbc;

    private UUID category;
    private UUID supplier;
    private UUID session;
    private final List<UUID> created = new ArrayList<>();
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it') RETURNING id",
            UUID.class, "it-cat-" + suffix);
        supplier = jdbc.queryForObject("INSERT INTO suppliers (code, name, created_by) VALUES (?, ?, 'it') RETURNING id",
            UUID.class, "IT-" + suffix, "it-sup-" + suffix);
        session = jdbc.queryForObject("INSERT INTO cash_sessions (status, opening_amount, opened_by, operation_date) "
            + "VALUES ('CERRADA', 0, 'it', '2001-01-01') RETURNING id", UUID.class);
    }

    private UUID product(String name, String unit, String status, String specialType) {
        UUID id = jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, "
            + "fixed_profit, percentage_profit, status, sale_unit, special_type, created_by) "
            + "VALUES (?, ?, ?, ?, 1, 2, 1, 100, ?, ?, ?, 'it') RETURNING id", UUID.class,
            "IT-" + name + "-" + suffix, name + " " + suffix, category, supplier, status, unit, specialType);
        created.add(id);
        return id;
    }

    private void sale(UUID product, int lines, LocalDate day, String status) {
        UUID sale = jdbc.queryForObject("INSERT INTO sales (operation_date, sale_number, cash_session_id, payment_method, "
            + "total_amount, status, created_by) VALUES (?, ?, ?, 'EFECTIVO', 2, ?, 'it') RETURNING id",
            UUID.class, Date.valueOf(day), "IT-B-" + UUID.randomUUID().toString().substring(0, 12), session, status);
        for (int i = 0; i < lines; i++) {
            jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
                + "quantity, subtotal) VALUES (?, ?, 'x', 2, 1, 2, 1, 2)", sale, product);
        }
    }

    private List<UUID> pendingOfThisTest() {
        return service.findPending().stream().map(BarcodePendingResponse::id).filter(created::contains).toList();
    }

    @Test
    void ordersByLinesOfTheLast30DaysAndThenByName() {
        LocalDate recent = StoreFormat.today().minusDays(2);
        UUID noSalesB = product("Bbb", "UNIT", "ACTIVO", null);
        UUID noSalesA = product("Aaa", "UNIT", "ACTIVO", null);
        UUID oneLine = product("Ccc", "UNIT", "ACTIVO", null);
        UUID threeLines = product("Ddd", "UNIT", "ACTIVO", null);
        sale(oneLine, 1, recent, "COMPLETADA");
        sale(threeLines, 3, recent, "COMPLETADA");
        // Neither an old sale nor a voided one counts
        sale(noSalesA, 9, recent.minusDays(60), "COMPLETADA");
        sale(noSalesB, 9, recent, "ANULADA");

        assertThat(pendingOfThisTest()).containsExactly(threeLines, oneLine, noSalesA, noSalesB);
    }

    @Test
    void leavesOutWhatDoesNotNeedACode() {
        UUID pending = product("Pendiente", "UNIT", "ACTIVO", null);
        product("Kilo", "KILOGRAM", "ACTIVO", null);
        product("Inactivo", "UNIT", "INACTIVO", null);
        product("Especial", "UNIT", "ACTIVO", "UNIDENTIFIED");
        UUID marked = product("Marcado", "UNIT", "ACTIVO", null);
        service.markNoBarcode(marked, "it");
        UUID withCode = product("Con codigo", "UNIT", "ACTIVO", null);
        service.addBarcode(withCode, "7791234567898", "it");

        assertThat(pendingOfThisTest()).containsExactly(pending);
    }

    @Test
    void aRemovedCodeLeavesTheProductPendingAndFreesTheCode() {
        UUID first = product("Primero", "UNIT", "ACTIVO", null);
        UUID second = product("Segundo", "UNIT", "ACTIVO", null);
        service.addBarcode(first, "7791234567898", "it");
        jdbc.update("UPDATE product_barcodes SET active = false, deactivated_by = 'it', deactivated_at = now() "
            + "WHERE product_id = ?", first);

        assertThat(service.addBarcode(second, "7791234567898", "it").barcodes()).containsExactly("7791234567898");
    }

    @Test
    void noSalesFigureTravelsInTheList() {
        assertThat(Arrays.stream(BarcodePendingResponse.class.getRecordComponents()).map(RecordComponent::getName))
            .containsExactlyInAnyOrder("id", "code", "name", "supplierName", "sellPrice");
    }
}
