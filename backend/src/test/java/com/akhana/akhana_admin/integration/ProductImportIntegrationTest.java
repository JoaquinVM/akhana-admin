package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.ProductImportResponse;
import com.akhana.akhana_admin.service.ProductImportService;
import jakarta.persistence.EntityManager;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAT-06 against the real database (unique indexes, presentations, price changes). Rolled back after each test.
 */
@SpringBootTest
@Transactional
class ProductImportIntegrationTest {

    @Autowired
    private ProductImportService importService;

    @Autowired
    private JdbcTemplate jdbc;

    // JdbcTemplate does not flush Hibernate's pending inserts (the real import commits)
    @Autowired
    private EntityManager entityManager;

    private static byte[] xlsx(Object[]... rows) {
        String[] header = {"Grupo", "Proveedor", "Etiqueta", "Nombre", "Codigo", "Costo", "Precio Venta", "Se vende por"};
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Productos");
            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                headerRow.createCell(i).setCellValue(header[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                XSSFRow row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (rows[r][c] != null) {
                        row.createCell(c).setCellValue((String) rows[r][c]);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Importa un fruto seco en Gramo con sus libras y un producto por unidad; repetir no duplica")
    void importsAndRepeats() {
        String tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String base = "IT" + tag + "-ARA";
        byte[] file = xlsx(
            new Object[]{"IT Nutricion " + tag, "IT Frutos " + tag, "IT Natural " + tag, "IT Arandano " + tag, base, 0.095, 0.14, "Gramo"},
            new Object[]{"IT Nutricion " + tag, "IT Frutos " + tag, null, "Arandano 1/4", base + "-CLI", 10.7, 16, null},
            new Object[]{"IT Nutricion " + tag, "IT Frutos " + tag, null, "Arandano 1/2", base + "-MLI", 21.5, 31.5, null},
            new Object[]{"IT Nutricion " + tag, "IT Frutos " + tag, null, "Arandano 1", base + "-1LI", 43, 63, null},
            new Object[]{"IT Nutricion " + tag, "IT Granel " + tag, null, "IT Psylium " + tag, "IT" + tag + "-PSY", 18, 25, null});

        ProductImportResponse first = importService.importProducts("Productos.xlsx", file, "it-import");
        entityManager.flush();

        assertThat(first.importedCount()).isEqualTo(2);
        assertThat(first.presentationCount()).isEqualTo(3);
        assertThat(first.rejectedCount()).isZero();
        assertThat(first.newSuppliers()).containsExactly("IT Frutos " + tag, "IT Granel " + tag);

        Map<String, Object> product = jdbc.queryForMap(
            "SELECT id, sale_unit, buy_price, sell_price, created_by FROM products WHERE code = ?", base);
        assertThat(product.get("sale_unit")).isEqualTo("KILOGRAM");
        assertThat((BigDecimal) product.get("buy_price")).isEqualByComparingTo("95.00");
        assertThat((BigDecimal) product.get("sell_price")).isEqualByComparingTo("140.00");
        assertThat(product.get("created_by")).isEqualTo("it-import");

        List<Map<String, Object>> presentations = jdbc.queryForList(
            "SELECT name, weight_kg, fixed_price FROM product_presentations WHERE product_id = ? AND active ORDER BY weight_kg",
            product.get("id"));
        assertThat(presentations).extracting(p -> p.get("name"))
            .containsExactly("100 g", "1/4 libra", "1/2 libra", "1 libra");
        assertThat(presentations.get(0).get("fixed_price")).isNull();
        assertThat((BigDecimal) presentations.get(2).get("fixed_price")).isEqualByComparingTo("31.50");

        Integer tags = jdbc.queryForObject(
            "SELECT COUNT(*) FROM product_tags WHERE product_id = ?", Integer.class, product.get("id"));
        assertThat(tags).isEqualTo(1);

        ProductImportResponse second = importService.importProducts("Productos.xlsx", file, "it-import");

        assertThat(second.importedCount()).isZero();
        assertThat(second.existingCount()).isEqualTo(5);
        assertThat(second.newSuppliers()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE code LIKE ?", Integer.class, "IT" + tag + "%"))
            .isEqualTo(2);
    }
}
