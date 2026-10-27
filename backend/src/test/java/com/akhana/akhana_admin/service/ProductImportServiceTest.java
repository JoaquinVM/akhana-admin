package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductImportResponse;
import com.akhana.akhana_admin.dto.ProductImportResponse.Row;
import com.akhana.akhana_admin.dto.ProductImportResponse.RowStatus;
import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CategoryRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.repository.TagRepository;
import com.akhana.akhana_admin.service.impl.ProductImportServiceImpl;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductImportServiceTest {

    private static final String[] HEADER = {"Grupo", "Proveedor", "Etiqueta", "Nombre", "Codigo", "Costo",
        "Precio Venta", "Utilidad", "Se vende por"};

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private TagRepository tagRepository;
    @Mock private ProductPresentationService presentationService;

    @InjectMocks
    private ProductImportServiceImpl importService;

    private final List<Product> savedProducts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(productRepository.findAllCodes()).thenReturn(List.of());
        when(productRepository.findNamesWithSupplier(ProductStatus.ELIMINADO)).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(supplierRepository.findAll()).thenReturn(List.of());
        when(tagRepository.findAll()).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(i -> i.getArgument(0));
        when(tagRepository.save(any(Tag.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            savedProducts.add(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    // Grupo, Proveedor, Etiqueta, Nombre, Codigo, Costo, Precio Venta, Utilidad, Se vende por
    private static Object[] row(String group, String supplier, String tag, String name, String code, Object cost,
                                Object price, String saleUnit) {
        return new Object[]{group, supplier, tag, name, code, cost, price, null, saleUnit};
    }

    private static byte[] xlsx(String[] header, Object[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Resumen");
            XSSFSheet sheet = workbook.createSheet("Productos");
            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                headerRow.createCell(i).setCellValue(header[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                XSSFRow sheetRow = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof Number number) {
                        sheetRow.createCell(c).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        sheetRow.createCell(c).setCellValue((String) value);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] xlsx(Object[]... rows) {
        return xlsx(HEADER, rows);
    }

    private static Row rowAt(ProductImportResponse response, int rowNumber) {
        return response.rows().stream().filter(r -> r.rowNumber() == rowNumber).findFirst()
            .orElseThrow(() -> new AssertionError("La fila " + rowNumber + " no está en el resultado"));
    }

    private Product saved(String code) {
        return savedProducts.stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Frutos secos en Gramo: convierte a Bs por kilo y sus libras son presentaciones, sin importar el orden")
    void gramProductWithPoundRows_becomesKiloProductWithPresentations() {
        byte[] file = xlsx(
            row("Nutricion", "Frutos Secos", null, "Arandano 1/2 libra", "FSE-ARA-MLI", 21.5, 31.5, null),
            row("Nutricion", "Frutos Secos", "Natural", "Arandano", "FSE-ARA", 0.095, 0.14, "Gramo"),
            row("Nutricion", "Frutos Secos", null, "Arandano 1/4 libra", "fse-ara-cli", 10.7, 16, null),
            row("Nutricion", "Frutos Secos", null, "Arandano 1 libra", "FSE-ARA-1LI", 43, 10, null));

        ProductImportResponse preview = importService.preview("Productos.xlsx", file);

        assertThat(preview.totalRows()).isEqualTo(4);
        assertThat(preview.importedCount()).isEqualTo(1);
        assertThat(preview.presentationCount()).isEqualTo(3);
        assertThat(preview.rejectedCount()).isZero();
        // 1 libra = 0,454 x 95 = 43,13 de costo: a Bs 10 se vende a pérdida
        assertThat(rowAt(preview, 5).status()).isEqualTo(RowStatus.WARNING);
        assertThat(rowAt(preview, 5).messages()).containsExactly("La presentación '1 libra' se vende a pérdida.");
        verify(productRepository, never()).save(any());

        importService.importProducts("Productos.xlsx", file, "admin");

        Product arandano = saved("FSE-ARA");
        assertThat(arandano.getSaleUnit()).isEqualTo(SaleUnit.KILOGRAM);
        assertThat(arandano.getBuyPrice()).isEqualByComparingTo("95.00");
        assertThat(arandano.getSellPrice()).isEqualByComparingTo("140.00");
        assertThat(arandano.getCreatedBy()).isEqualTo("admin");
        assertThat(savedProducts).hasSize(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductPresentationRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(presentationService).applyPresentations(eq(arandano), captor.capture(), isNull(), eq("admin"));
        assertThat(captor.getValue()).extracting(ProductPresentationRequest::name, ProductPresentationRequest::weightKg,
                ProductPresentationRequest::fixedPrice)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("100 g", new BigDecimal("0.100"), null),
                org.assertj.core.groups.Tuple.tuple("1/4 libra", new BigDecimal("0.113"), new BigDecimal("16.00")),
                org.assertj.core.groups.Tuple.tuple("1/2 libra", new BigDecimal("0.227"), new BigDecimal("31.50")),
                org.assertj.core.groups.Tuple.tuple("1 libra", new BigDecimal("0.454"), new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("Sin Se vende por, el precio por gramo se rechaza y arrastra a sus presentaciones")
    void gramPriceAsUnit_isRejectedWithItsPresentations() {
        ProductImportResponse response = importService.preview("p.xlsx", xlsx(
            row("Nutricion", "Frutos Secos", null, "Arandano", "FSE-ARA", 0.095, 0.14, null),
            row("Nutricion", "Frutos Secos", null, "Arandano 1/2 libra", "FSE-ARA-MLI", 21.5, 31.5, null),
            row("Nutricion", "Frutos Secos", null, "Nuez 1/2 libra", "FSE-NUE-MLI", 29.48, 45, null)));

        assertThat(response.importedCount()).isZero();
        assertThat(response.rejectedCount()).isEqualTo(3);
        assertThat(rowAt(response, 2).messages()).containsExactly(
            "Precio menor a Bs 1: si se vende por peso, marcar en Se vende por Gramo, 100 g o Kilo.");
        assertThat(rowAt(response, 3).messages()).containsExactly("Se rechazó el producto base FSE-ARA (fila 2).");
        assertThat(rowAt(response, 4).messages()).containsExactly("No está el producto base FSE-NUE en el archivo.");
    }

    @Test
    @DisplayName("Presentación de una base por unidad o repetida se rechaza")
    void presentationOfUnitBaseOrRepeated_isRejected() {
        ProductImportResponse response = importService.preview("p.xlsx", xlsx(
            row("Nutricion", "Frutos Secos", null, "Mani", "FSE-MAN", 20, 30, null),
            row("Nutricion", "Frutos Secos", null, "Mani 1/2 libra", "FSE-MAN-MLI", 5, 8, null),
            row("Nutricion", "Frutos Secos", null, "Nuez", "FSE-NUE", 130, 210, "Kilo"),
            row("Nutricion", "Frutos Secos", null, "Nuez 1/2 libra", "FSE-NUE-MLI", 29.48, 45, null),
            row("Nutricion", "Frutos Secos", null, "Nuez 1/2 libra", "FSE-NUE-MLI", 29.48, 46, null)));

        assertThat(rowAt(response, 3).messages()).containsExactly("El producto base FSE-MAN se vende por unidad: marcar Kilo.");
        assertThat(rowAt(response, 6).messages()).containsExactly("La presentación 1/2 libra de FSE-NUE ya está en la fila 5.");
        assertThat(response.presentationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Granel: por unidad sin columna, × 10 con 100 g")
    void bulkProduct_unitOrHundredGrams() {
        importService.importProducts("p.xlsx", xlsx(
            row("Nutricion", "Granel", null, "Psylium 100gr", "GRA-PSY", 18, 25, null),
            row("Nutricion", "Granel", null, "Espirulina 100gr", "GRA-ESP", 30, 45.5, "100 gr")), "admin");

        assertThat(saved("GRA-PSY").getSaleUnit()).isEqualTo(SaleUnit.UNIT);
        assertThat(saved("GRA-PSY").getSellPrice()).isEqualByComparingTo("25.00");
        assertThat(saved("GRA-ESP").getSaleUnit()).isEqualTo(SaleUnit.KILOGRAM);
        assertThat(saved("GRA-ESP").getBuyPrice()).isEqualByComparingTo("300.00");
        assertThat(saved("GRA-ESP").getSellPrice()).isEqualByComparingTo("455.00");
        verify(presentationService, never()).applyPresentations(eq(saved("GRA-PSY")), any(), any(), any());
    }

    @Test
    @DisplayName("Reglas por fila: faltantes, números, decimales, costo 0, pérdida y código repetido")
    void rowRules() {
        ProductImportResponse response = importService.preview("p.xlsx", xlsx(
            row("Nutricion", "Desconocida 1", null, "Oleo Primula Capsulas", null, 40, 60, null),
            row("Estetica", "Natuser", null, "Crema Facial Rosa Mosqueta", "EST-CRE", 30, null, null),
            row("Estetica", "Natuser", null, "Lip care frutos rojos", "EST-LIP", 0, 15, null),
            row("Nutricion", "Natuser", null, "Barra Chia Caja x12", "NAT-BAR-CAJ", 45, 44, null),
            row("Nutricion", "Quintos", null, "Quintos Hojuela", "COR-QUI-HOC-70GR", 5, 8, null),
            row("Nutricion", "Quintos", null, "Quintos Rulito", "cor-qui-hoc-70gr", 5, 8, null),
            row("Nutricion", "Quintos", null, "Quintos Texto", "COR-QUI-TXT", "abc", "12,50", null),
            row("Nutricion", "Quintos", null, "Quintos Decimales", "COR-QUI-DEC", 5, 12.345, null),
            row("Nutricion", "Frutos Secos", null, "Cajú", "FSE-CAJ", 0.0951234, 0.14, "g"),
            row("Nutricion", "Frutos Secos", null, "Pasas", "FSE-PAS", 3, 5, "Kilo"),
            row("Nutricion", "Frutos Secos", null, "Pasas 2", "FSE-PA2", 3, 5, "Libra"),
            row("Nutricion", "Quintos", null, "Quintos Gratis", "COR-QUI-GRA", -1, 0, null)));

        assertThat(rowAt(response, 2).messages()).containsExactly("Falta el código.");
        assertThat(rowAt(response, 3).messages()).containsExactly("Falta el precio de venta.");
        assertThat(rowAt(response, 4).status()).isEqualTo(RowStatus.WARNING);
        assertThat(rowAt(response, 4).messages()).containsExactly("Costo 0: toda la venta cuenta como utilidad.");
        assertThat(rowAt(response, 5).messages()).containsExactly("Se vende a pérdida.");
        assertThat(rowAt(response, 7).messages()).containsExactly("El código ya está en la fila 6.");
        assertThat(rowAt(response, 8).messages()).containsExactly("Costo no es un número: 'abc'.");
        assertThat(rowAt(response, 9).messages()).containsExactly("Precio Venta admite hasta 2 decimales: '12.345'.");
        assertThat(rowAt(response, 10).messages()).containsExactly(
            "Costo convertido a Bs por kilo tiene más de 2 decimales: '0.0951234'.");
        assertThat(rowAt(response, 11).messages()).containsExactly(
            "Precio por kilo menor a Bs 10: revisar Se vende por (¿Gramo o 100 g?).");
        assertThat(rowAt(response, 12).messages()).containsExactly("Se vende por debe ser Unidad, Gramo, 100 g o Kilo.");
        assertThat(rowAt(response, 13).messages()).containsExactly("Precio Venta debe ser mayor a 0.", "Costo no puede ser negativo.");
        assertThat(response.importedCount()).isEqualTo(3);
        assertThat(response.warningCount()).isEqualTo(2);
        assertThat(response.rejectedCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("Texto con coma decimal se acepta")
    void decimalCommaText_isAccepted() {
        importService.importProducts("p.xlsx", xlsx(
            row("Nutricion", "Quintos", null, "Quintos Coma", "COR-QUI-COM", "7,30", "12,50", null)), "admin");

        assertThat(saved("COR-QUI-COM").getBuyPrice()).isEqualByComparingTo("7.30");
        assertThat(saved("COR-QUI-COM").getSellPrice()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("Mismo nombre de otro proveedor se renombra; del mismo proveedor se rechaza")
    void repeatedNames() {
        ProductImportResponse response = importService.preview("p.xlsx", xlsx(
            row("Nutricion", "Sativa", null, "Kefir Natural 2L", "SAT-KEF", 20, 30, null),
            row("Nutricion", "Belen", null, "Kefir Natural 2L", "BEL-KEF", 18, 28, null),
            row("Nutricion", "Sativa", null, "kefir natural 2l", "SAT-KEF-2", 20, 30, null),
            row("Nutricion", "Belen", null, "Kefir Natural 2L", "BEL-KEF-2", 18, 28, null)));

        assertThat(rowAt(response, 3).status()).isEqualTo(RowStatus.WARNING);
        assertThat(rowAt(response, 3).messages()).containsExactly("Nombre repetido: se guardó como «Kefir Natural 2L (Belen)».");
        assertThat(rowAt(response, 4).messages()).containsExactly("El nombre ya está en la fila 2.");
        assertThat(rowAt(response, 5).messages()).containsExactly("El nombre ya está en la fila 3.");
        assertThat(response.importedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Nombre del catálogo de otro proveedor se renombra; del mismo, se rechaza")
    void repeatedNamesAgainstCatalog() {
        when(productRepository.findNamesWithSupplier(ProductStatus.ELIMINADO))
            .thenReturn(List.<Object[]>of(new Object[]{"Cardo Mariano", "Autentica"}));

        ProductImportResponse response = importService.preview("p.xlsx", xlsx(
            row("Nutricion", "autentica ", null, "Cardo Mariano", "AUT-CAR", 20, 30, null),
            row("Nutricion", "Natuser", null, "Cardo Mariano", "NAT-CAR", 20, 30, null)));

        assertThat(rowAt(response, 2).messages()).containsExactly("Ya existe un producto con el nombre Cardo Mariano.");
        assertThat(rowAt(response, 3).messages()).containsExactly("Nombre repetido: se guardó como «Cardo Mariano (Natuser)».");
    }

    @Test
    @DisplayName("Categorías, proveedores y etiquetas nuevos se crean una vez, sin distinguir mayúsculas")
    void newReferences_areCreatedOnce() {
        Category nutricion = Category.builder().id(UUID.randomUUID()).name("Nutricion").status(CategoryStatus.ACTIVO).build();
        when(categoryRepository.findAll()).thenReturn(List.of(nutricion));
        when(supplierRepository.findAll()).thenReturn(List.of(
            Supplier.builder().name("Granel").code("ALFAVITAMINS").status(SupplierStatus.ACTIVO).build()));

        ProductImportResponse response = importService.importProducts("p.xlsx", xlsx(
            row("nutricion", "Alfavitamins", "Vegano", "Vitamina C", "ALF-VIC", 20, 30, null),
            row("Estetica", " AlfaVitamins ", "vegano", "Vitamina D", "ALF-VID", 20, 30, null)), "admin");

        assertThat(response.newCategories()).containsExactly("Estetica");
        assertThat(response.newSuppliers()).containsExactly("Alfavitamins");
        assertThat(response.newTags()).containsExactly("Vegano");
        assertThat(saved("ALF-VIC").getCategory()).isSameAs(nutricion);
        assertThat(saved("ALF-VIC").getSupplier()).isSameAs(saved("ALF-VID").getSupplier());
        assertThat(saved("ALF-VIC").getSupplier().getCode()).isEqualTo("ALFAVITAMINS-2");
        assertThat(saved("ALF-VID").getTags()).extracting(Tag::getName).containsExactly("Vegano");
        assertThat(saved("ALF-VID").getCategory().getColor()).isEqualTo("#475569");
        verify(supplierRepository, times(1)).save(any(Supplier.class));
    }

    @Test
    @DisplayName("Una categoría o proveedor dado de baja no se reactiva: la fila se rechaza")
    void retiredReferences_rejectTheRow() {
        when(categoryRepository.findAll()).thenReturn(List.of(
            Category.builder().name("Bebidas").status(CategoryStatus.ELIMINADO).build()));
        when(supplierRepository.findAll()).thenReturn(List.of(
            Supplier.builder().name("Sativa").code("SAT").status(SupplierStatus.INACTIVO).build()));

        ProductImportResponse response = importService.preview("p.xlsx", xlsx(
            row("bebidas", "Belen", null, "Jugo", "BEL-JUG", 5, 8, null),
            row("Nutricion", "Sativa", null, "Kefir", "SAT-KEF", 5, 8, null)));

        assertThat(rowAt(response, 2).messages()).containsExactly("La categoría Bebidas está dada de baja.");
        assertThat(rowAt(response, 3).messages()).containsExactly("El proveedor Sativa está dado de baja.");
        assertThat(response.newCategories()).isEmpty();
    }

    @Test
    @DisplayName("Repetir la importación: lo ya importado sale como ya existente y no se guarda")
    void repeatedImport_countsExistingApart() {
        when(productRepository.findAllCodes()).thenReturn(List.of("FSE-ARA", "GRA-PSY"));

        ProductImportResponse response = importService.importProducts("p.xlsx", xlsx(
            row("Nutricion", "Frutos Secos", null, "Arandano", "fse-ara", 0.095, 0.14, "Gramo"),
            row("Nutricion", "Frutos Secos", null, "Arandano 1/2 libra", "FSE-ARA-MLI", 21.5, 31.5, null),
            row("Nutricion", "Granel", null, "Psylium 100gr", "GRA-PSY", 18, 25, null),
            row("Nutricion", "Granel", null, "Espirulina 100gr", "GRA-ESP", 30, 45, null)), "admin");

        assertThat(response.existingCount()).isEqualTo(3);
        assertThat(response.rejectedCount()).isZero();
        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(rowAt(response, 2).messages()).containsExactly("Ya existe un producto con el código fse-ara.");
        assertThat(rowAt(response, 3).messages()).containsExactly(
            "El producto FSE-ARA ya existe: agregar la presentación desde Productos.");
        assertThat(savedProducts).extracting(Product::getCode).containsExactly("GRA-ESP");
    }

    @Test
    @DisplayName("Columnas por nombre: en otro orden, con tildes y mayúsculas; faltantes se informan")
    void columnsByName() {
        String[] shuffled = {"PRECIO VENTA", "Código", "nombre", "Costo", "Proveedor", "Grupo"};
        importService.importProducts("p.xlsx", xlsx(shuffled, new Object[]{30, "COD-1", "Moringa ", 20, "Autentica", "Nutricion"}), "admin");
        assertThat(saved("COD-1").getName()).isEqualTo("Moringa");
        assertThat(saved("COD-1").getSaleUnit()).isEqualTo(SaleUnit.UNIT);

        assertThatThrownBy(() -> importService.preview("p.xlsx", xlsx(new String[]{"Grupo", "Nombre", "Codigo"})))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Faltan las columnas: Proveedor, Costo, Precio Venta.");
    }

    @Test
    @DisplayName("Filas vacías se saltan sin contarlas")
    void emptyRows_areSkipped() {
        ProductImportResponse response = importService.preview("p.xlsx", xlsx(
            row("Nutricion", "Granel", null, "Psylium", "GRA-PSY", 18, 25, null),
            row(null, null, null, null, null, null, null, null),
            row(null, null, null, null, null, 5, null, null)));

        assertThat(response.totalRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un archivo que no es .xlsx se rechaza entero")
    void invalidFile_isRejected() {
        assertThatThrownBy(() -> importService.preview("p.csv", xlsx(new Object[0][])))
            .hasMessage("El archivo debe ser un .xlsx de hasta 5 MB y 5.000 filas.");
        assertThatThrownBy(() -> importService.preview("p.xlsx", "Grupo,Proveedor".getBytes()))
            .hasMessage("El archivo debe ser un .xlsx de hasta 5 MB y 5.000 filas.");
        assertThatThrownBy(() -> importService.preview("p.xlsx", new byte[5 * 1024 * 1024 + 1]))
            .hasMessage("El archivo debe ser un .xlsx de hasta 5 MB y 5.000 filas.");
    }

    @Test
    @DisplayName("Más de 5.000 filas se rechaza entero")
    void tooManyRows_isRejected() {
        Object[][] rows = new Object[5001][];
        Arrays.fill(rows, row("Nutricion", "Granel", null, "X", "X", 1, 2, null));
        assertThatThrownBy(() -> importService.preview("p.xlsx", xlsx(rows)))
            .hasMessage("El archivo debe ser un .xlsx de hasta 5 MB y 5.000 filas.");
    }
}
