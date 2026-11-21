package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.BoxConversionRequest;
import com.akhana.akhana_admin.dto.BoxProposalResponse;
import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.dto.ProductPresentationResponse;
import com.akhana.akhana_admin.dto.ProductRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.SaleUnit;
import com.akhana.akhana_admin.service.BoxProductConversionService;
import com.akhana.akhana_admin.service.ProductBarcodeService;
import com.akhana.akhana_admin.service.ProductService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAT-15 and COM-10 against PostgreSQL: the barcodes of packages share the unique index of the products' barcodes, and a
 * box sold as its own product becomes a package of its base product. Every row is rolled back at the end of each test.
 */
@SpringBootTest
@Transactional
class PackagingIntegrationTest {

    private static final String USER = "it-packaging";

    @Autowired private ProductService productService;
    @Autowired private ProductBarcodeService barcodeService;
    @Autowired private BoxProductConversionService conversionService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private String tag;
    private UUID category;
    private UUID supplier;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT cat " + tag, USER);
        supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT Natuser " + tag, "ITN-" + tag, USER);
    }

    @Test
    @DisplayName("CAT-15: el código de un empaque es único entre productos y empaques, no es del producto y quitar el empaque lo libera")
    void packageBarcodesShareTheUniqueIndex() {
        String productCode = ean13("779100" + digits(6));
        String packageCode = ean13("779200" + digits(6));
        ProductResponse tea = product("TE", "Té verde " + tag, "10.00", "15.00", SaleUnit.UNIT, List.of(), List.of(productCode));

        assertThatThrownBy(() -> product("BAR-X", "Barra Chia X " + tag, "2.13", "4.00", SaleUnit.UNIT,
                List.of(pkg("Caja ×12", "12", "44.00", true, productCode)), List.of()))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessage("El código ya es de " + tea.name() + " (" + tea.code() + ").");

        ProductResponse bar = product("BAR", "Barra Chia " + tag, "2.13", "4.00", SaleUnit.UNIT,
            List.of(pkg("Caja ×12", "12", "44.00", true, packageCode)), List.of());
        assertThat(bar.barcodes()).isEmpty();
        assertThat(bar.presentations()).singleElement().satisfies(p -> {
            assertThat(p.barcode()).isEqualTo(packageCode);
            assertThat(p.price()).isEqualByComparingTo("44.00");
            assertThat(p.cost()).isEqualByComparingTo("25.56");
        });
        assertThat(barcodeService.findByBarcode(packageCode).id()).isEqualTo(bar.id());

        assertThatThrownBy(() -> product("ROQ-X", "Roquin X " + tag, "2.73", "4.00", SaleUnit.UNIT, List.of(), List.of(packageCode)))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessage("El código ya es de " + bar.name() + " · Caja ×12.");

        // A product by kilo has no barcode of its own, but its packages may (Bolsa 250 g from the factory)
        String bagCode = ean13("779300" + digits(6));
        ProductResponse nut = product("NUE", "Nuez " + tag, "130.00", "210.00", SaleUnit.KILOGRAM,
            List.of(pkg("Bolsa 250 g", "0.250", null, true, bagCode)), List.of());
        assertThat(nut.presentations()).extracting(ProductPresentationResponse::barcode).containsExactly(bagCode);

        // Removing the package frees its code for another product
        productService.updateProduct(bar.id(), request("BAR", bar.name(), "2.13", "4.00", SaleUnit.UNIT, List.of(), null), USER);
        entityManager.flush();
        ProductResponse roquin = product("ROQ", "Roquin " + tag, "2.73", "4.00", SaleUnit.UNIT, List.of(), List.of(packageCode));
        assertThat(roquin.barcodes()).containsExactly(packageCode);
    }

    @Test
    @DisplayName("COM-10: propone Barra Chia Caja x12 por su código; confirmar crea la caja que se vende con su código y elimina el producto caja")
    void convertsABoxProductIntoAPackage() {
        String boxCode = ean13("779400" + digits(6));
        ProductResponse bar = product("BAR-CHI", "Barra Chia " + tag, "2.13", "4.00", SaleUnit.UNIT, List.of(), List.of());
        ProductResponse box = product("BAR-CHI-CAJ", "Barra Chia " + tag + " Caja x12", "25.50", "44.00", SaleUnit.UNIT,
            List.of(), List.of(boxCode));
        // A tea box of 50 grams is no box of units, and a product without a base is not proposed
        product("TEV-CJA", "Te Verde " + tag + " x 50 gr Cja", "15.00", "22.00", SaleUnit.UNIT, List.of(), List.of());
        product("ROQ-CAJ", "Roquin " + tag + " Caja x24", "65.50", "88.00", SaleUnit.UNIT, List.of(), List.of());
        UUID pastSale = pastSaleOf(box.id());

        List<BoxProposalResponse> proposals = conversionService.findProposals().stream()
            .filter(p -> p.boxName().contains(tag)).toList();
        assertThat(proposals).singleElement().satisfies(p -> {
            assertThat(p.boxProductId()).isEqualTo(box.id());
            assertThat(p.baseProductId()).isEqualTo(bar.id());
            assertThat(p.proposedName()).isEqualTo("Caja ×12");
            assertThat(p.proposedBaseQuantity()).isEqualByComparingTo("12");
            assertThat(p.proposedPrice()).isEqualByComparingTo("44.00");
            assertThat(p.boxBarcodes()).containsExactly(boxCode);
        });

        ProductResponse base = conversionService.convert(box.id(),
            new BoxConversionRequest("Caja ×12", new BigDecimal("12"), new BigDecimal("44.00"), boxCode), USER);
        entityManager.flush();

        assertThat(base.id()).isEqualTo(bar.id());
        assertThat(base.presentations()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Caja ×12");
            assertThat(p.sold()).isTrue();
            assertThat(p.price()).isEqualByComparingTo("44.00");
            assertThat(p.barcode()).isEqualTo(boxCode);
        });
        ProductResponse deleted = productService.getProductById(box.id());
        assertThat(deleted.status()).isEqualTo(ProductStatus.ELIMINADO);
        assertThat(deleted.deletedBy()).isEqualTo(USER);
        assertThat(deleted.barcodes()).isEmpty();
        assertThat(barcodeService.findByBarcode(boxCode).id()).isEqualTo(bar.id());
        // Its past sale still points to the box, as it was sold
        assertThat(jdbc.queryForObject("SELECT product_name FROM sale_items WHERE sale_id = ?", String.class, pastSale))
            .isEqualTo(box.name());
        assertThat(jdbc.queryForObject("SELECT decision FROM box_product_reviews WHERE box_product_id = ?", String.class,
            box.id())).isEqualTo("CONVERTED");
        assertThat(conversionService.findProposals()).noneMatch(p -> p.boxProductId().equals(box.id()));
        assertThatThrownBy(() -> conversionService.convert(box.id(),
            new BoxConversionRequest("Caja ×12", new BigDecimal("12"), null, null), USER))
            .hasMessage("Esta caja ya se revisó.");
    }

    @Test
    @DisplayName("COM-10: descartar saca la caja de la lista y sigue siendo un producto; un empaque inválido no cambia nada")
    void discardsAndValidatesBoxes() {
        ProductResponse bar = product("BAR-MAN", "Barra Mani " + tag, "2.38", "4.00", SaleUnit.UNIT,
            List.of(pkg("Docena", "12", null, true, null)), List.of());
        ProductResponse box = product("BAR-MAN-CAJ", "Barra Mani " + tag + " Caja x12", "28.50", "44.00", SaleUnit.UNIT,
            List.of(), List.of());

        // Barra Mani already has a package of 12 units: nothing changes and the box stays
        assertThatThrownBy(() -> conversionService.convert(box.id(),
            new BoxConversionRequest("Caja ×12", new BigDecimal("12"), new BigDecimal("44.00"), null), USER))
            .hasMessage("Ya existe un empaque de 12 u en este producto.");

        conversionService.discard(box.id(), USER);

        assertThat(productService.getProductById(box.id()).status()).isEqualTo(ProductStatus.ACTIVO);
        assertThat(productService.getProductById(bar.id()).presentations()).hasSize(1);
        assertThat(conversionService.findProposals()).noneMatch(p -> p.boxProductId().equals(box.id()));
    }

    private ProductResponse product(String code, String name, String buy, String sell, SaleUnit unit,
                                    List<ProductPresentationRequest> packages, List<String> barcodes) {
        return productService.createProduct(request(code, name, buy, sell, unit, packages, barcodes), USER);
    }

    private ProductRequest request(String code, String name, String buy, String sell, SaleUnit unit,
                                   List<ProductPresentationRequest> packages, List<String> barcodes) {
        return new ProductRequest("IT" + tag + "-" + code, name, category, supplier, null, List.of(), new BigDecimal(buy),
            new BigDecimal(sell), ProductStatus.ACTIVO, unit, packages, null, null, barcodes);
    }

    private static ProductPresentationRequest pkg(String name, String quantity, String price, boolean sold, String barcode) {
        return new ProductPresentationRequest(null, name, new BigDecimal(quantity), price != null ? new BigDecimal(price) : null,
            true, sold, barcode);
    }

    private UUID pastSaleOf(UUID product) {
        UUID session = jdbc.queryForObject("INSERT INTO cash_sessions (status, opening_amount, opened_by, operation_date) "
            + "VALUES ('CERRADA', 0, ?, '2001-06-01') RETURNING id", UUID.class, USER);
        UUID sale = jdbc.queryForObject("INSERT INTO sales (sale_number, cash_session_id, payment_method, sale_type, "
                + "total_amount, subtotal_amount, amount_cash, amount_qr, credit_amount, created_by, created_at, operation_date) "
                + "VALUES (?, ?, 'EFECTIVO', 'NORMAL', 44, 44, 44, 0, 0, ?, ?, ?) RETURNING id",
            UUID.class, "IT-" + tag, session, USER, Timestamp.from(Instant.parse("2001-06-01T15:00:00Z")),
            LocalDate.of(2001, 6, 1));
        String name = jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, product);
        jdbc.update("INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, unit_cost, final_unit_price, "
            + "quantity, subtotal) VALUES (?, ?, ?, 44, 25.50, 44, 1, 44)", sale, product, name);
        return sale;
    }

    private static String digits(int count) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < count; i++) {
            value.append((int) (Math.random() * 10));
        }
        return value.toString();
    }

    // EAN-13 with its check digit
    private static String ean13(String twelve) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = twelve.charAt(i) - '0';
            sum += i % 2 == 0 ? digit : digit * 3;
        }
        return twelve + (10 - sum % 10) % 10;
    }
}
