package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.dto.ProductWebPublicationChangeResponse;
import com.akhana.akhana_admin.model.ProductStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// WEB-01, WEB-02, CAT-13 against PostgreSQL and real files: the public copy is what leaves the VPS
@SpringBootTest
class WebCatalogExportIntegrationTest {

    @TempDir
    static Path photosDir;

    @TempDir
    static Path catalogDir;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        registry.add("app.product-photos.dir", () -> photosDir.toString());
        registry.add("app.web-catalog.dir", () -> catalogDir.toString());
    }

    @Autowired private WebCatalogExportService exportService;
    @Autowired private ProductWebPublicationService publicationService;
    @Autowired private ProductPhotoService photoService;
    @Autowired private ProductService productService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private String suffix;
    private UUID categoryId;
    private UUID supplierId;
    private UUID creditorId;
    private final List<UUID> products = new ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        categoryId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, color, status, created_by, created_at) VALUES (?, ?, '#111111', 'ACTIVO', 'test', now())",
            categoryId, "Categoria " + suffix);
        jdbc.update("INSERT INTO suppliers (id, name, code, status, created_by, created_at) VALUES (?, ?, ?, 'ACTIVO', 'test', now())",
            supplierId, "ProveedorSecreto " + suffix, "W" + suffix);
        creditorId = UUID.randomUUID();
        jdbc.update("INSERT INTO creditors (id, name, created_by) VALUES (?, ?, 'test')", creditorId, "Duena " + suffix);
    }

    @AfterEach
    void tearDown() {
        for (UUID id : products) {
            jdbc.update("DELETE FROM product_web_publication_changes WHERE product_id = ?", id);
            jdbc.update("DELETE FROM product_presentations WHERE product_id = ?", id);
            jdbc.update("DELETE FROM product_photos WHERE product_id = ?", id);
            jdbc.update("DELETE FROM products WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
        jdbc.update("DELETE FROM suppliers WHERE id = ?", supplierId);
        jdbc.update("DELETE FROM creditors WHERE id = ?", creditorId);
    }

    private UUID product(String label, String saleUnit, String specialType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO products (id, code, name, category_id, supplier_id, description, buy_price, sell_price, fixed_profit,
                percentage_profit, status, sale_unit, special_type, owner_creditor_id, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, 'Rica en fibra', 1234.56, 2000, 765.44, 62, 'ACTIVO', ?, ?, ?, 'usuarioSecreto', now())""",
            id, label + "-" + suffix, label + " " + suffix, categoryId, supplierId, saleUnit, specialType,
            specialType != null ? creditorId : null);
        products.add(id);
        return id;
    }

    private UUID publishedProduct(String label) throws IOException {
        UUID id = product(label, "UNIT", null);
        photoService.upload(id, photo(), null, "ana");
        publicationService.mark(id, "ana");
        return id;
    }

    private static byte[] photo() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        return bytes.toByteArray();
    }

    private JsonNode exported(UUID id) throws IOException {
        exportService.export();
        JsonNode catalog = objectMapper.readTree(Files.readAllBytes(catalogDir.resolve("catalog.json")));
        String code = jdbc.queryForObject("SELECT code FROM products WHERE id = ?", String.class, id);
        for (JsonNode item : catalog.get("products")) {
            if (code.equals(item.get("code").asString())) {
                return item;
            }
        }
        return null;
    }

    private Set<String> photoFiles() throws IOException {
        try (Stream<Path> files = Files.list(catalogDir.resolve("photos"))) {
            return files.map(file -> file.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    @Test
    @DisplayName("WEB-01: sale el producto activo, marcado y con foto, con su foto y su miniatura")
    void export_publishesActiveMarkedWithPhoto() throws IOException {
        UUID id = publishedProduct("Chia");
        UUID photoId = productService.getProductById(id).photoId();

        assertThat(exported(id)).isNotNull();
        assertThat(photoFiles()).contains(photoId + ".jpg", photoId + "-thumb.jpg");
    }

    @Test
    @DisplayName("WEB-01: sin marca no sale")
    void export_skipsUnmarked() throws IOException {
        UUID id = product("SinMarca", "UNIT", null);
        photoService.upload(id, photo(), null, "ana");

        assertThat(exported(id)).isNull();
    }

    @Test
    @DisplayName("WEB-01: sin foto no sale, aunque esté marcado")
    void export_skipsWithoutPhoto() throws IOException {
        UUID id = product("SinFoto", "UNIT", null);
        publicationService.mark(id, "ana");

        assertThat(exported(id)).isNull();
    }

    @Test
    @DisplayName("WEB-01: inactivo o eliminado no sale, y reactivarlo lo devuelve")
    void export_skipsInactiveAndDeleted() throws IOException {
        UUID inactive = publishedProduct("Inactivo");
        jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", inactive);
        assertThat(exported(inactive)).isNull();

        jdbc.update("UPDATE products SET status = 'ACTIVO' WHERE id = ?", inactive);
        assertThat(exported(inactive)).isNotNull();

        UUID deleted = publishedProduct("Eliminado");
        jdbc.update("UPDATE products SET status = 'ELIMINADO' WHERE id = ?", deleted);
        assertThat(exported(deleted)).isNull();
    }

    @Test
    @DisplayName("WEB-01: si falta el archivo de la foto, el producto no sale y la exportación sigue")
    void export_skipsLostFile() throws IOException {
        UUID lost = publishedProduct("Perdida");
        UUID kept = publishedProduct("Guardada");
        UUID photoId = productService.getProductById(lost).photoId();
        Files.delete(photosDir.resolve(photoId + ".jpg"));

        assertThat(exported(lost)).isNull();
        assertThat(exported(kept)).isNotNull();
    }

    @Test
    @DisplayName("WEB-01: un producto especial no se puede marcar y uno eliminado tampoco")
    void mark_rejectsSpecialAndDeleted() {
        UUID special = product("Tercero", "UNIT", "THIRD_PARTY");
        assertThatThrownBy(() -> publicationService.mark(special, "ana"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un producto especial no se publica en la web.");

        UUID deleted = product("Borrado", "UNIT", null);
        jdbc.update("UPDATE products SET status = 'ELIMINADO' WHERE id = ?", deleted);
        assertThatThrownBy(() -> publicationService.mark(deleted, "ana"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un producto eliminado no se publica en la web.");
    }

    @Test
    @DisplayName("WEB-01: un especial marcado directo en la base tampoco sale")
    void export_skipsSpecialEvenIfMarked() throws IOException {
        UUID special = product("Tercero2", "UNIT", "THIRD_PARTY");
        photoService.upload(special, photo(), null, "ana");
        jdbc.update("UPDATE products SET web_published = TRUE WHERE id = ?", special);

        assertThat(exported(special)).isNull();
    }

    @Test
    @DisplayName("WEB-02: el JSON lleva exactamente los campos permitidos y nada de costo, proveedor ni usuarios")
    void export_carriesOnlyAllowedFields() throws IOException {
        UUID id = publishedProduct("Campos");

        JsonNode item = exported(id);

        assertThat(item.propertyNames()).containsExactlyInAnyOrder(
            "code", "name", "category", "description", "saleUnit", "price", "photo", "presentations");
        assertThat(item.get("price").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(item.get("category").asString()).isEqualTo("Categoria " + suffix);

        String raw = Files.readString(catalogDir.resolve("catalog.json"));
        assertThat(raw).doesNotContain("1234.56", "765.44", "ProveedorSecreto", "usuarioSecreto", "buyPrice",
            "supplier", "createdBy", "\"ana\"");
    }

    @Test
    @DisplayName("WEB-02, WEB-05: las presentaciones activas salen con nombre y precio, sin peso ni costo")
    void export_carriesActivePresentations() throws IOException {
        UUID id = product("Almendra", "KILOGRAM", null);
        photoService.upload(id, photo(), null, "ana");
        publicationService.mark(id, "ana");
        jdbc.update("""
            INSERT INTO product_presentations (id, product_id, name, weight_kg, fixed_price, active, created_by, created_at)
            VALUES (?, ?, 'Media libra', 0.227, NULL, TRUE, 'test', now()),
                   (?, ?, 'Vieja', 0.5, 99, FALSE, 'test', now())""",
            UUID.randomUUID(), id, UUID.randomUUID(), id);

        JsonNode item = exported(id);

        assertThat(item.get("saleUnit").asString()).isEqualTo("KILOGRAM");
        assertThat(item.get("presentations")).hasSize(1);
        JsonNode presentation = item.get("presentations").get(0);
        assertThat(presentation.propertyNames()).containsExactlyInAnyOrder("name", "price");
        assertThat(presentation.get("name").asString()).isEqualTo("Media libra");
        assertThat(presentation.get("price").decimalValue()).isEqualByComparingTo("454.00");
    }

    @Test
    @DisplayName("ADM-03: desmarcar quita el producto y su foto de la copia, sin tocar la foto de Admin")
    void unmark_removesProductAndPhotoFromCopy() throws IOException {
        UUID id = publishedProduct("Quitar");
        UUID photoId = productService.getProductById(id).photoId();
        assertThat(exported(id)).isNotNull();

        publicationService.unmark(id, "ana");

        assertThat(exported(id)).isNull();
        assertThat(photoFiles()).doesNotContain(photoId + ".jpg", photoId + "-thumb.jpg");
        assertThat(photosDir.resolve(photoId + ".jpg")).exists();
    }

    @Test
    @DisplayName("CAT-13: cada cambio guarda quién y cuándo, y repetir el mismo estado no agrega nada")
    void mark_recordsWhoAndWhen() {
        UUID id = product("Auditoria", "UNIT", null);

        ProductResponse marked = publicationService.mark(id, "ana");
        publicationService.mark(id, "ana");
        ProductResponse unmarked = publicationService.unmark(id, "beto");

        assertThat(marked.webPublished()).isTrue();
        assertThat(unmarked.webPublished()).isFalse();
        List<ProductWebPublicationChangeResponse> changes = publicationService.history(id);
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).published()).isFalse();
        assertThat(changes.get(0).changedBy()).isEqualTo("beto");
        assertThat(changes.get(1).published()).isTrue();
        assertThat(changes.get(1).changedBy()).isEqualTo("ana");
        assertThat(changes.get(1).changedAt()).isNotNull();
    }

    @Test
    @DisplayName("Editar el producto no le quita la marca")
    void editing_keepsTheMark() {
        UUID id = product("Editar", "UNIT", null);
        publicationService.mark(id, "ana");

        productService.changeProductStatus(id, ProductStatus.INACTIVO, "ana");

        assertThat(productService.getProductById(id).webPublished()).isTrue();
    }

    @Test
    @DisplayName("Sin cambios el catálogo conserva su contenido y renueva su hora, que el script usa para alertar")
    void export_refreshesTimeWithoutRewriting() throws IOException {
        publishedProduct("Hora");
        exportService.export();
        Path catalog = catalogDir.resolve("catalog.json");
        byte[] before = Files.readAllBytes(catalog);
        Files.setLastModifiedTime(catalog, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));

        exportService.export();

        assertThat(Files.readAllBytes(catalog)).isEqualTo(before);
        assertThat(Files.getLastModifiedTime(catalog).toInstant()).isAfter(Instant.parse("2021-01-01T00:00:00Z"));
    }
}
