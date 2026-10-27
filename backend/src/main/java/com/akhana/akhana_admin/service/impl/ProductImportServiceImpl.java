package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ProductImportResponse;
import com.akhana.akhana_admin.dto.ProductImportResponse.Row;
import com.akhana.akhana_admin.dto.ProductImportResponse.RowStatus;
import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CategoryRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.repository.TagRepository;
import com.akhana.akhana_admin.service.ProductImportService;
import com.akhana.akhana_admin.service.ProductPresentationService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * CAT-06: reads the Productos sheet (columns found by name), validates each row against the previous rows and the
 * catalog, converts weight prices to Bs per kilo (CAT-08) and turns the -CLI/-MLI/-1LI rows of Frutos Secos into
 * presentations of their base product (CAT-11). Detail and messages in specs/product-import.md.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductImportServiceImpl implements ProductImportService {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_ROWS = 5000;
    static final String DEFAULT_COLOR = "#475569";
    static final BigDecimal MIN_UNIT_PRICE = BigDecimal.ONE;
    static final BigDecimal MIN_KILO_PRICE = BigDecimal.TEN;
    static final String ZERO_COST = "Costo 0: toda la venta cuenta como utilidad.";
    static final String LOSS = "Se vende a pérdida.";
    static final String BELOW_ONE = "Precio menor a Bs 1: si se vende por peso, marcar en Se vende por Gramo, 100 g o Kilo.";
    static final String BELOW_TEN_PER_KILO = "Precio por kilo menor a Bs 10: revisar Se vende por (¿Gramo o 100 g?).";
    static final String BAD_SALE_UNIT = "Se vende por debe ser Unidad, Gramo, 100 g o Kilo.";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final TagRepository tagRepository;
    private final ProductPresentationService presentationService;

    enum Column {
        GROUP("Grupo", true, "el grupo"),
        SUPPLIER("Proveedor", true, "el proveedor"),
        TAG("Etiqueta", false, "la etiqueta"),
        NAME("Nombre", true, "el nombre"),
        CODE("Codigo", true, "el código"),
        COST("Costo", true, "el costo"),
        PRICE("Precio Venta", true, "el precio de venta"),
        SALE_UNIT("Se vende por", false, null);

        final String header;
        final boolean required;
        final String missing;

        Column(String header, boolean required, String missing) {
            this.header = header;
            this.required = required;
            this.missing = missing;
        }
    }

    /** How the spreadsheet prices a product and the factor that turns them into Bs per unit or per kilo. */
    record SaleUnitFactor(SaleUnit saleUnit, BigDecimal factor) {}

    /** CAT-11: fixed name and weight of the pound presentations, by the end of the code. */
    record PoundPresentation(String suffix, String name, BigDecimal weightKg) {}

    static final List<PoundPresentation> POUND_PRESENTATIONS = List.of(
        new PoundPresentation("-CLI", "1/4 libra", new BigDecimal("0.113")),
        new PoundPresentation("-MLI", "1/2 libra", new BigDecimal("0.227")),
        new PoundPresentation("-1LI", "1 libra", new BigDecimal("0.454")));
    static final String HALF_POUND = "-MLI";
    static final String HUNDRED_GRAMS = "100 g";

    record SheetRow(int rowNumber, Map<Column, String> values) {
        String get(Column column) {
            return values.getOrDefault(column, "");
        }
    }

    record PresentationDraft(int rowNumber, PoundPresentation pound, BigDecimal fixedPrice) {}

    static final class ProductDraft {
        final int rowNumber;
        final String code;
        final String name;
        final String categoryKey;
        final String supplierKey;
        final String tagKey;
        final BigDecimal buyPrice;
        final BigDecimal sellPrice;
        final SaleUnit saleUnit;
        final Map<String, PresentationDraft> presentations = new LinkedHashMap<>();

        ProductDraft(int rowNumber, String code, String name, String categoryKey, String supplierKey, String tagKey,
                     BigDecimal buyPrice, BigDecimal sellPrice, SaleUnit saleUnit) {
            this.rowNumber = rowNumber;
            this.code = code;
            this.name = name;
            this.categoryKey = categoryKey;
            this.supplierKey = supplierKey;
            this.tagKey = tagKey;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.saleUnit = saleUnit;
        }
    }

    /** Active entities by lower-case name, names given up (not reactivated) and the ones the import creates. */
    static final class Reference<T> {
        final Map<String, T> active = new HashMap<>();
        final Map<String, String> retired = new HashMap<>();
        final Map<String, String> created = new LinkedHashMap<>();
        final String retiredMessage;

        Reference(String retiredMessage) {
            this.retiredMessage = retiredMessage;
        }
    }

    record NameOwner(Integer rowNumber, String supplierKey) {}

    /** A whole-file error: nothing is imported. */
    static final class FileRejected extends IllegalArgumentException {
        FileRejected(String message) {
            super(message);
        }
    }

    static final class Analysis {
        final List<Row> rows = new ArrayList<>();
        final List<ProductDraft> products = new ArrayList<>();
        final Reference<Category> categories = new Reference<>("La categoría %s está dada de baja.");
        final Reference<Supplier> suppliers = new Reference<>("El proveedor %s está dado de baja.");
        final Reference<Tag> tags = new Reference<>("La etiqueta %s está dada de baja.");
        final Map<String, String> supplierNames = new HashMap<>();
        int totalRows;
        int presentationCount;
        int warningCount;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductImportResponse preview(String fileName, byte[] content) {
        return toResponse(analyze(fileName, content));
    }

    @Override
    public ProductImportResponse importProducts(String fileName, byte[] content, String username) {
        Analysis analysis = analyze(fileName, content);
        save(analysis, username);
        return toResponse(analysis);
    }

    // --- Reading ---

    private Analysis analyze(String fileName, byte[] content) {
        List<SheetRow> sheetRows = read(fileName, content);
        Analysis analysis = new Analysis();
        loadReferences(analysis);

        Set<String> existingCodes = new HashSet<>();
        productRepository.findAllCodes().forEach(code -> existingCodes.add(key(code)));
        Map<String, NameOwner> names = new HashMap<>();
        for (Object[] nameAndSupplier : productRepository.findNamesWithSupplier(ProductStatus.ELIMINADO)) {
            names.put(key((String) nameAndSupplier[0]), new NameOwner(null, key((String) nameAndSupplier[1])));
        }

        Map<String, Integer> firstRowByCode = new HashMap<>();
        Map<String, ProductDraft> acceptedByCode = new HashMap<>();
        Map<String, Integer> rejectedByCode = new HashMap<>();
        List<SheetRow> presentationRows = new ArrayList<>();

        for (SheetRow row : sheetRows) {
            analysis.totalRows++;
            if (poundOf(row.get(Column.CODE)) != null) {
                // Resolved after every product row is read, so the order does not matter
                presentationRows.add(row);
                continue;
            }
            analyzeProduct(row, analysis, existingCodes, names, firstRowByCode, acceptedByCode, rejectedByCode);
        }
        for (SheetRow row : presentationRows) {
            analyzePresentation(row, analysis, existingCodes, acceptedByCode, rejectedByCode);
        }
        analysis.rows.sort(Comparator.comparingInt(Row::rowNumber));
        return analysis;
    }

    private List<SheetRow> read(String fileName, byte[] content) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")
            || content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException(INVALID_FILE);
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = productsSheet(workbook);
            if (sheet.getLastRowNum() > MAX_ROWS) {
                throw new FileRejected(INVALID_FILE);
            }
            Map<Column, Integer> columns = columns(sheet.getRow(0));
            List<SheetRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row sheetRow = sheet.getRow(i);
                Map<Column, String> values = new EnumMap<>(Column.class);
                columns.forEach((column, index) -> values.put(column, sheetRow == null ? "" : text(sheetRow.getCell(index))));
                boolean empty = values.get(Column.GROUP).isEmpty() && values.get(Column.SUPPLIER).isEmpty()
                    && values.get(Column.NAME).isEmpty() && values.get(Column.CODE).isEmpty();
                if (!empty) {
                    rows.add(new SheetRow(i + 1, values));
                }
            }
            return rows;
        } catch (FileRejected e) {
            throw e;
        } catch (Exception e) {
            // POI rejects a file that is not an .xlsx with its own exceptions
            throw new IllegalArgumentException(INVALID_FILE);
        }
    }

    private static Sheet productsSheet(Workbook workbook) {
        for (Sheet sheet : workbook) {
            if (normalize(sheet.getSheetName()).equals("productos")) {
                return sheet;
            }
        }
        return workbook.getSheetAt(0);
    }

    private static Map<Column, Integer> columns(org.apache.poi.ss.usermodel.Row header) {
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        if (header != null) {
            for (Cell cell : header) {
                String title = normalize(text(cell));
                for (Column column : Column.values()) {
                    if (title.equals(normalize(column.header))) {
                        columns.putIfAbsent(column, cell.getColumnIndex());
                    }
                }
            }
        }
        List<String> missing = Arrays.stream(Column.values())
            .filter(column -> column.required && !columns.containsKey(column))
            .map(column -> column.header)
            .toList();
        if (!missing.isEmpty()) {
            throw new FileRejected("Faltan las columnas: " + String.join(", ", missing) + ".");
        }
        return columns;
    }

    /** The real value of the cell (not the one its format shows), as text. */
    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> clean(cell.getStringCellValue());
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private void loadReferences(Analysis analysis) {
        for (Category category : categoryRepository.findAll()) {
            register(analysis.categories, category.getName(), category, category.getStatus() == CategoryStatus.ACTIVO);
        }
        for (Supplier supplier : supplierRepository.findAll()) {
            // An inactive supplier keeps its name taken (unique index), so it blocks like a deleted one
            register(analysis.suppliers, supplier.getName(), supplier, supplier.getStatus() == SupplierStatus.ACTIVO);
            if (supplier.getStatus() == SupplierStatus.ACTIVO) {
                analysis.supplierNames.put(key(supplier.getName()), supplier.getName());
            }
        }
        for (Tag tag : tagRepository.findAll()) {
            register(analysis.tags, tag.getName(), tag, tag.getStatus() == TagStatus.ACTIVO);
        }
    }

    private static <T> void register(Reference<T> reference, String name, T entity, boolean active) {
        if (active) {
            reference.active.put(key(name), entity);
            reference.retired.remove(key(name));
        } else if (!reference.active.containsKey(key(name))) {
            reference.retired.put(key(name), name);
        }
    }

    // --- Product rows ---

    private void analyzeProduct(SheetRow row, Analysis analysis, Set<String> existingCodes, Map<String, NameOwner> names,
                                Map<String, Integer> firstRowByCode, Map<String, ProductDraft> acceptedByCode,
                                Map<String, Integer> rejectedByCode) {
        String code = row.get(Column.CODE);
        String name = row.get(Column.NAME);
        List<String> errors = new ArrayList<>();

        if (!code.isEmpty()) {
            if (existingCodes.contains(key(code))) {
                analysis.rows.add(new Row(row.rowNumber(), code, name, RowStatus.EXISTING,
                    List.of("Ya existe un producto con el código " + code + ".")));
                return;
            }
            Integer firstRow = firstRowByCode.putIfAbsent(key(code), row.rowNumber());
            if (firstRow != null) {
                errors.add("El código ya está en la fila " + firstRow + ".");
            }
        }
        for (Column column : List.of(Column.GROUP, Column.SUPPLIER, Column.NAME, Column.CODE, Column.COST, Column.PRICE)) {
            if (row.get(column).isEmpty()) {
                errors.add("Falta " + column.missing + ".");
            }
        }
        if (name.length() > 150) {
            errors.add("El nombre no debe superar los 150 caracteres.");
        }
        if (code.length() > 50) {
            errors.add("El código no debe superar los 50 caracteres.");
        }
        for (Column column : List.of(Column.GROUP, Column.SUPPLIER, Column.TAG)) {
            if (row.get(column).length() > 150) {
                errors.add(column.header + " no debe superar los 150 caracteres.");
            }
        }

        SaleUnitFactor unit = saleUnit(row.get(Column.SALE_UNIT));
        if (unit == null) {
            errors.add(BAD_SALE_UNIT);
        }
        BigDecimal cost = number(row.get(Column.COST), Column.COST, errors);
        BigDecimal price = number(row.get(Column.PRICE), Column.PRICE, errors);
        BigDecimal buyPrice = null;
        BigDecimal sellPrice = null;
        if (unit != null && cost != null && price != null) {
            buyPrice = cost.multiply(unit.factor());
            sellPrice = price.multiply(unit.factor());
            boolean converted = unit.factor().compareTo(BigDecimal.ONE) != 0;
            if (sellPrice.signum() <= 0) {
                errors.add("Precio Venta debe ser mayor a 0.");
            }
            if (buyPrice.signum() < 0) {
                errors.add("Costo no puede ser negativo.");
            }
            if (unit.saleUnit() == SaleUnit.UNIT && sellPrice.signum() > 0 && sellPrice.compareTo(MIN_UNIT_PRICE) < 0) {
                // A price per gram imported as a price per unit: marking the unit fixes the decimals too
                errors.add(BELOW_ONE);
            } else {
                checkScale(buyPrice, row.get(Column.COST), Column.COST, converted, errors);
                checkScale(sellPrice, row.get(Column.PRICE), Column.PRICE, converted, errors);
                if (unit.saleUnit() == SaleUnit.KILOGRAM && sellPrice.signum() > 0
                    && sellPrice.compareTo(MIN_KILO_PRICE) < 0) {
                    errors.add(BELOW_TEN_PER_KILO);
                }
            }
        }

        Map<Reference<?>, String> pendingNew = new HashMap<>();
        String categoryKey = reference(row.get(Column.GROUP), analysis.categories, pendingNew, errors);
        String supplierKey = reference(row.get(Column.SUPPLIER), analysis.suppliers, pendingNew, errors);
        String tagKey = reference(row.get(Column.TAG), analysis.tags, pendingNew, errors);

        List<String> warnings = new ArrayList<>();
        String finalName = name;
        if (errors.isEmpty()) {
            String supplierName = analysis.supplierNames.getOrDefault(supplierKey, row.get(Column.SUPPLIER));
            finalName = resolveName(name, supplierKey, supplierName, names, errors, warnings);
        }

        if (!errors.isEmpty()) {
            if (!code.isEmpty()) {
                rejectedByCode.putIfAbsent(key(code), row.rowNumber());
            }
            analysis.rows.add(new Row(row.rowNumber(), code, name, RowStatus.REJECTED, errors));
            return;
        }

        buyPrice = buyPrice.setScale(2, RoundingMode.UNNECESSARY);
        sellPrice = sellPrice.setScale(2, RoundingMode.UNNECESSARY);
        if (buyPrice.signum() == 0) {
            warnings.add(ZERO_COST);
        }
        if (sellPrice.compareTo(buyPrice) < 0) {
            warnings.add(LOSS);
        }
        pendingNew.forEach((reference, display) -> reference.created.putIfAbsent(key(display), display));
        if (pendingNew.containsKey(analysis.suppliers)) {
            analysis.supplierNames.putIfAbsent(supplierKey, pendingNew.get(analysis.suppliers));
        }
        names.put(key(finalName), new NameOwner(row.rowNumber(), supplierKey));

        ProductDraft draft = new ProductDraft(row.rowNumber(), code, finalName, categoryKey, supplierKey, tagKey,
            buyPrice, sellPrice, unit.saleUnit());
        analysis.products.add(draft);
        acceptedByCode.put(key(code), draft);
        addWarnings(analysis, row.rowNumber(), code, finalName, warnings);
    }

    /** CAT-01 does not allow repeated names: another supplier's product is saved as «name (supplier)». */
    private static String resolveName(String name, String supplierKey, String supplierName, Map<String, NameOwner> names,
                               List<String> errors, List<String> warnings) {
        NameOwner owner = names.get(key(name));
        if (owner == null) {
            return name;
        }
        if (owner.supplierKey().equals(supplierKey)) {
            errors.add(nameTaken(name, owner));
            return name;
        }
        String renamed = name + " (" + supplierName + ")";
        if (renamed.length() > 150) {
            errors.add("El nombre no debe superar los 150 caracteres.");
            return name;
        }
        NameOwner renamedOwner = names.get(key(renamed));
        if (renamedOwner != null) {
            errors.add(nameTaken(renamed, renamedOwner));
            return name;
        }
        warnings.add("Nombre repetido: se guardó como «" + renamed + "».");
        return renamed;
    }

    private static String nameTaken(String name, NameOwner owner) {
        return owner.rowNumber() == null
            ? "Ya existe un producto con el nombre " + name + "."
            : "El nombre ya está en la fila " + owner.rowNumber() + ".";
    }

    /** Lower-case key of an active or new category, supplier or tag; a retired one rejects the row. */
    private static String reference(String name, Reference<?> reference, Map<Reference<?>, String> pendingNew,
                                    List<String> errors) {
        if (name.isEmpty()) {
            return null;
        }
        String key = key(name);
        if (reference.active.containsKey(key)) {
            return key;
        }
        if (reference.retired.containsKey(key)) {
            errors.add(reference.retiredMessage.formatted(reference.retired.get(key)));
            return null;
        }
        // A new one keeps the first spelling of the file
        pendingNew.put(reference, reference.created.getOrDefault(key, name));
        return key;
    }

    // --- Presentation rows (CAT-11) ---

    private void analyzePresentation(SheetRow row, Analysis analysis, Set<String> existingCodes,
                                     Map<String, ProductDraft> acceptedByCode, Map<String, Integer> rejectedByCode) {
        String code = row.get(Column.CODE);
        String name = row.get(Column.NAME);
        PoundPresentation pound = poundOf(code);
        String baseCode = code.substring(0, code.length() - pound.suffix().length());

        if (existingCodes.contains(key(code))) {
            analysis.rows.add(new Row(row.rowNumber(), code, name, RowStatus.EXISTING,
                List.of("Ya existe un producto con el código " + code + ".")));
            return;
        }
        if (existingCodes.contains(key(baseCode))) {
            analysis.rows.add(new Row(row.rowNumber(), code, name, RowStatus.EXISTING,
                List.of("El producto " + baseCode + " ya existe: agregar la presentación desde Productos.")));
            return;
        }

        List<String> errors = new ArrayList<>();
        BigDecimal price = null;
        if (row.get(Column.PRICE).isEmpty()) {
            errors.add("Falta " + Column.PRICE.missing + ".");
        } else {
            price = number(row.get(Column.PRICE), Column.PRICE, errors);
            if (price != null) {
                if (price.signum() <= 0) {
                    errors.add("Precio Venta debe ser mayor a 0.");
                }
                checkScale(price, row.get(Column.PRICE), Column.PRICE, false, errors);
            }
        }

        ProductDraft base = acceptedByCode.get(key(baseCode));
        if (errors.isEmpty()) {
            if (base == null) {
                Integer rejectedRow = rejectedByCode.get(key(baseCode));
                errors.add(rejectedRow != null
                    ? "Se rechazó el producto base " + baseCode + " (fila " + rejectedRow + ")."
                    : "No está el producto base " + baseCode + " en el archivo.");
            } else if (base.saleUnit != SaleUnit.KILOGRAM) {
                errors.add("El producto base " + baseCode + " se vende por unidad: marcar Kilo.");
            } else if (base.presentations.containsKey(pound.suffix())) {
                errors.add("La presentación " + pound.name() + " de " + baseCode + " ya está en la fila "
                    + base.presentations.get(pound.suffix()).rowNumber() + ".");
            }
        }
        if (!errors.isEmpty()) {
            analysis.rows.add(new Row(row.rowNumber(), code, name, RowStatus.REJECTED, errors));
            return;
        }

        BigDecimal fixedPrice = price.setScale(2, RoundingMode.UNNECESSARY);
        base.presentations.put(pound.suffix(), new PresentationDraft(row.rowNumber(), pound, fixedPrice));
        analysis.presentationCount++;
        // CAT-11: the cost is always weight x cost per kilo of the base product
        BigDecimal cost = pound.weightKg().multiply(base.buyPrice).setScale(2, RoundingMode.HALF_UP);
        List<String> warnings = fixedPrice.compareTo(cost) < 0
            ? List.of("La presentación '" + pound.name() + "' se vende a pérdida.")
            : List.of();
        addWarnings(analysis, row.rowNumber(), code, name, warnings);
    }

    private static PoundPresentation poundOf(String code) {
        String upper = code.toUpperCase(Locale.ROOT);
        return POUND_PRESENTATIONS.stream()
            .filter(p -> upper.endsWith(p.suffix()) && upper.length() > p.suffix().length())
            .findFirst()
            .orElse(null);
    }

    private static void addWarnings(Analysis analysis, int rowNumber, String code, String name, List<String> warnings) {
        if (!warnings.isEmpty()) {
            analysis.warningCount++;
            analysis.rows.add(new Row(rowNumber, code, name, RowStatus.WARNING, List.copyOf(warnings)));
        }
    }

    // --- Values ---

    /** CAT-08: «Se vende por» says the unit of Costo and Precio Venta; weight prices become Bs per kilo. */
    static SaleUnitFactor saleUnit(String value) {
        String normalized = normalize(value).replace(" ", "").replace(".", "");
        return switch (normalized) {
            case "", "unidad", "unidades", "u" -> new SaleUnitFactor(SaleUnit.UNIT, BigDecimal.ONE);
            case "gramo", "gramos", "g", "gr", "grs" -> new SaleUnitFactor(SaleUnit.KILOGRAM, new BigDecimal("1000"));
            case "100g", "100gr", "100grs", "100gramos" -> new SaleUnitFactor(SaleUnit.KILOGRAM, BigDecimal.TEN);
            case "kilo", "kilos", "kg", "kilogramo", "kilogramos" -> new SaleUnitFactor(SaleUnit.KILOGRAM, BigDecimal.ONE);
            default -> null;
        };
    }

    /** A number from the cell, or text with a decimal point or comma; null (with the error) otherwise. */
    private static BigDecimal number(String value, Column column, List<String> errors) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            errors.add(column.header + " no es un número: '" + value + "'.");
            return null;
        }
    }

    private static void checkScale(BigDecimal value, String original, Column column, boolean converted,
                                   List<String> errors) {
        if (value.stripTrailingZeros().scale() > 2) {
            errors.add(converted
                ? column.header + " convertido a Bs por kilo tiene más de 2 decimales: '" + original + "'."
                : column.header + " admite hasta 2 decimales: '" + original + "'.");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace(' ', ' ').strip();
    }

    private static String key(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    /** Without case, accents or repeated spaces: for column titles, the sheet name and «Se vende por». */
    private static String normalize(String value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    // --- Saving ---

    private void save(Analysis analysis, String username) {
        Instant now = Instant.now();
        Map<String, Category> categories = new HashMap<>(analysis.categories.active);
        analysis.categories.created.forEach((key, name) -> categories.put(key, categoryRepository.save(Category.builder()
            .name(name).color(DEFAULT_COLOR).status(CategoryStatus.ACTIVO).createdBy(username).createdAt(now).build())));

        Map<String, Supplier> suppliers = new HashMap<>(analysis.suppliers.active);
        Set<String> supplierCodes = new HashSet<>();
        supplierRepository.findAll().stream()
            .filter(s -> s.getStatus() != SupplierStatus.ELIMINADO)
            .forEach(s -> supplierCodes.add(key(s.getCode())));
        analysis.suppliers.created.forEach((key, name) -> suppliers.put(key, supplierRepository.save(Supplier.builder()
            .name(name).code(supplierCode(name, supplierCodes)).status(SupplierStatus.ACTIVO)
            .createdBy(username).createdAt(now).build())));

        Map<String, Tag> tags = new HashMap<>(analysis.tags.active);
        analysis.tags.created.forEach((key, name) -> tags.put(key, tagRepository.save(Tag.builder()
            .name(name).color(DEFAULT_COLOR).status(TagStatus.ACTIVO).createdBy(username).createdAt(now).build())));

        for (ProductDraft draft : analysis.products) {
            BigDecimal fixedProfit = ProductServiceImpl.calculateFixedProfit(draft.buyPrice, draft.sellPrice);
            Product product = productRepository.save(Product.builder()
                .code(draft.code)
                .name(draft.name)
                .category(categories.get(draft.categoryKey))
                .supplier(suppliers.get(draft.supplierKey))
                .tags(draft.tagKey == null ? new HashSet<>() : new HashSet<>(Set.of(tags.get(draft.tagKey))))
                .buyPrice(draft.buyPrice)
                .sellPrice(draft.sellPrice)
                .fixedProfit(fixedProfit)
                .percentageProfit(ProductServiceImpl.calculatePercentageProfit(draft.buyPrice, fixedProfit))
                .status(ProductStatus.ACTIVO)
                .saleUnit(draft.saleUnit)
                .createdBy(username)
                .createdAt(now)
                .build());
            if (draft.saleUnit == SaleUnit.KILOGRAM) {
                presentationService.applyPresentations(product, presentationRequests(draft), null, username);
            }
        }
    }

    /** 100 g as the form proposes, the pound rows of the file and 1/2 libra when the file has none (CAT-11). */
    private static List<ProductPresentationRequest> presentationRequests(ProductDraft draft) {
        List<ProductPresentationRequest> requests = new ArrayList<>();
        requests.add(new ProductPresentationRequest(null, HUNDRED_GRAMS, new BigDecimal("0.100"), null, true));
        draft.presentations.values().forEach(p ->
            requests.add(new ProductPresentationRequest(null, p.pound().name(), p.pound().weightKg(), p.fixedPrice(), true)));
        if (!draft.presentations.containsKey(HALF_POUND)) {
            PoundPresentation half = POUND_PRESENTATIONS.stream().filter(p -> p.suffix().equals(HALF_POUND)).findFirst().orElseThrow();
            requests.add(new ProductPresentationRequest(null, half.name(), half.weightKg(), null, true));
        }
        return requests;
    }

    /** Suppliers need a unique code: the name in capitals without accents, numbered if it is taken. */
    static String supplierCode(String name, Set<String> takenCodes) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "PROV";
        }
        base = base.length() > 45 ? base.substring(0, 45) : base;
        String code = base;
        for (int n = 2; takenCodes.contains(key(code)); n++) {
            code = base + "-" + n;
        }
        takenCodes.add(key(code));
        return code;
    }

    private static ProductImportResponse toResponse(Analysis analysis) {
        Function<RowStatus, Integer> count = status -> (int) analysis.rows.stream().filter(r -> r.status() == status).count();
        return new ProductImportResponse(
            analysis.totalRows,
            analysis.products.size(),
            analysis.presentationCount,
            count.apply(RowStatus.EXISTING),
            count.apply(RowStatus.REJECTED),
            analysis.warningCount,
            List.copyOf(analysis.categories.created.values()),
            List.copyOf(analysis.suppliers.created.values()),
            List.copyOf(analysis.tags.created.values()),
            List.copyOf(analysis.rows));
    }
}
