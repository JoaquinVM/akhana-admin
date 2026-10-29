package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.MigrationResponse;
import com.akhana.akhana_admin.dto.MigrationResponse.*;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.MigrationService;
import com.akhana.akhana_admin.service.impl.MigrationWorkbook.Column;
import com.akhana.akhana_admin.service.impl.MigrationWorkbook.FileRejected;
import com.akhana.akhana_admin.service.impl.MigrationWorkbook.SheetRow;
import com.akhana.akhana_admin.service.impl.ProductImportServiceImpl.PoundPresentation;
import com.akhana.akhana_admin.service.impl.ProductImportServiceImpl.SaleUnitFactor;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.akhana.akhana_admin.service.impl.MigrationWorkbook.*;

/**
 * FIN-11: reads Ventas, Transacc., Cierre de Caja, Consumos, Pedidos and Detalle Pedidos, validates every row
 * against the catalog, accounts, categories and suppliers, and saves everything as historical records in one
 * transaction, only when no row is rejected. Detail and messages in specs/migration.md.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MigrationServiceImpl implements MigrationService {

    static final ZoneId STORE_ZONE = ZoneId.of("America/La_Paz");
    static final String VENTAS = "Ventas";
    static final String TRANSACC = "Transacc.";
    static final String CIERRE = "Cierre de Caja";
    static final String CONSUMOS = "Consumos";
    static final String PEDIDOS = "Pedidos";
    static final String DETALLE = "Detalle Pedidos";
    static final String PRODUCTOS = "Productos";
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    static final List<Column> SALE_COLUMNS = List.of(
        new Column("date", true, "Fecha"),
        new Column("code", true, "Codigo"),
        new Column("qty", true, "Cant", "Cantidad"),
        new Column("name", true, "Descripción"),
        new Column("total", true, "Total"),
        new Column("comment", false, "Comentarios"),
        new Column("cost", true, "Costo unitario"));
    static final List<Column> MOVEMENT_COLUMNS = List.of(
        new Column("type", true, "Tipo"),
        new Column("date", true, "Fecha"),
        new Column("description", true, "Descripcion"),
        new Column("amount", true, "Monto"),
        new Column("category", true, "Categoria"),
        new Column("account", true, "Cuenta"),
        new Column("destination", true, "Cuenta Destino"),
        new Column("comment", false, "Comentarios"));
    // The date of Cierre de Caja is the first column, without a title
    static final List<Column> CLOSING_COLUMNS = List.of(
        new Column("date", false, "Fecha"),
        new Column("opening", true, "Caja Inicial"),
        new Column("otherIncome", true, "Otro Ingreso"),
        new Column("total", true, "Total"),
        new Column("qr", true, "QR"),
        new Column("cash", true, "Efectivo"),
        new Column("expenses", true, "Gastos Caja"),
        new Column("otherOutflow", true, "Otra salida"),
        new Column("internalOutflow", true, "Salida Interna"),
        new Column("result", true, "Resultado"),
        new Column("closing", true, "Caja final"),
        new Column("surplus", true, "Sobrante"),
        new Column("comment", false, "Comentarios"));
    static final Set<String> CLOSING_AMOUNTS = Set.of("otherIncome", "total", "qr", "cash", "expenses",
        "otherOutflow", "internalOutflow", "result", "surplus");
    static final List<Column> CONSUMPTION_COLUMNS = List.of(
        new Column("date", true, "Fecha"),
        new Column("code", true, "Codigo"),
        new Column("qty", true, "Cantidad"),
        new Column("cost", true, "Costo unitario"),
        new Column("name", false, "Descripción"),
        new Column("for", false, "Para quien"),
        new Column("comment", false, "Comentarios"));
    static final List<Column> ORDER_COLUMNS = List.of(
        new Column("order", true, "COD"),
        new Column("name", false, "Nombre"),
        new Column("supplier", true, "Proveedor"),
        new Column("amount", true, "Monto"),
        new Column("shipping", false, "Monto Envio"),
        new Column("payType", false, "Tipo Pago"),
        new Column("payStatus", true, "Estado Pago"),
        new Column("payDate", false, "Fecha pago"),
        new Column("paid", false, "Monto pagado"),
        new Column("deliveryType", false, "Tipo Entrega"),
        new Column("deliveryStatus", false, "Estado de Entrega"),
        new Column("deliveryDate", false, "Fecha Entrega"),
        new Column("comment", false, "Comentarios"));
    // Detalle Pedidos has two Codigo: the first is the order (or COD) and the second the product
    static final List<Column> DETAIL_COLUMNS = List.of(
        new Column("order", true, "COD", "Codigo"),
        new Column("date", true, "Fecha"),
        new Column("supplier", false, "Provedor", "Proveedor"),
        new Column("code", true, "Codigo"),
        new Column("qty", true, "Cant", "Cantidad"),
        new Column("cost", true, "Precio unitario"),
        new Column("total", true, "Total"));
    static final List<Column> PRODUCT_COLUMNS = List.of(
        new Column("code", true, "Codigo"),
        new Column("saleUnit", false, "Se vende por"));

    private final ProductRepository productRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final SupplierRepository supplierRepository;
    private final SaleRepository saleRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CashSessionRepository cashSessionRepository;
    private final ConsumptionRepository consumptionRepository;
    private final PurchaseRepository purchaseRepository;
    private final HistoricalImportRepository historicalImportRepository;

    /** Rows of one sheet: read, to import, rejected and after the cutoff. */
    static final class Count {
        int read;
        int toImport;
        int rejected;
        int afterCutoff;
    }

    record SaleKey(LocalDate date, SaleType type) implements Comparable<SaleKey> {
        @Override
        public int compareTo(SaleKey other) {
            int byDate = date.compareTo(other.date);
            return byDate != 0 ? byDate : type.compareTo(other.type);
        }
    }

    record ConsumptionDraft(LocalDate date, String consumedFor, String comment, ConsumptionItem item) {}

    /** Product and quantity of a line, and what turns the cost of the spreadsheet into the system's (CAT-08, CAT-11). */
    record ResolvedLine(Product product, ProductPresentation presentation, BigDecimal quantity, BigDecimal weightKg,
                        BigDecimal costFactor) {
        BigDecimal unitCost(BigDecimal sheetCost) {
            return sheetCost.multiply(costFactor).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal costQuantity() {
            return weightKg != null ? weightKg : quantity;
        }
    }

    static final class MonthTotalsDraft {
        BigDecimal sales = ZERO;
        BigDecimal profit = ZERO;
        BigDecimal unidentified = ZERO;
        BigDecimal atCost = ZERO;
        BigDecimal thirdParty = ZERO;
        BigDecimal consumptions = ZERO;
    }

    static final class Analysis {
        final LocalDate cutoffDate;
        final Map<String, Count> counts = new LinkedHashMap<>();
        final List<RowMessage> rejected = new ArrayList<>();
        final List<RowMessage> notices = new ArrayList<>();
        final SortedMap<SaleKey, List<SaleItem>> sales = new TreeMap<>();
        final List<ConsumptionDraft> consumptions = new ArrayList<>();
        final List<FinancialTransaction> transactions = new ArrayList<>();
        final List<CashSession> sessions = new ArrayList<>();
        final List<Purchase> purchases = new ArrayList<>();
        final SortedMap<String, MonthTotalsDraft> months = new TreeMap<>();
        final SortedMap<String, SortedMap<String, BigDecimal>> movements = new TreeMap<>();
        final Map<String, BigDecimal> thirdPartyDebts = new TreeMap<>();
        int unidentifiedLines;
        BigDecimal unidentifiedTotal = ZERO;

        // Reference data
        final Map<String, Product> products = new HashMap<>();
        Product unidentified;
        final Map<String, SaleUnitFactor> saleUnits = new HashMap<>();
        final Map<String, FinancialAccount> accounts = new HashMap<>();
        final Map<String, List<FinancialCategory>> categories = new HashMap<>();
        final Map<String, Supplier> suppliers = new HashMap<>();
        FinancialAccount cashAccount;
        FinancialAccount qrAccount;

        Analysis(LocalDate cutoffDate) {
            this.cutoffDate = cutoffDate;
            for (String sheet : List.of(VENTAS, TRANSACC, CIERRE, CONSUMOS, PEDIDOS, DETALLE)) {
                counts.put(sheet, new Count());
            }
        }

        MonthTotalsDraft month(LocalDate date) {
            return months.computeIfAbsent(date.toString().substring(0, 7), m -> new MonthTotalsDraft());
        }

        void movement(LocalDate date, String concept, BigDecimal amount) {
            movements.computeIfAbsent(date.toString().substring(0, 7), m -> new TreeMap<>())
                .merge(concept, amount, BigDecimal::add);
        }

        /** True when the row is imported; otherwise it is listed as rejected. */
        boolean accept(String sheet, SheetRow row, List<String> errors) {
            if (errors.isEmpty()) {
                counts.get(sheet).toImport++;
                return true;
            }
            counts.get(sheet).rejected++;
            rejected.add(new RowMessage(sheet, row.rowNumber(), List.copyOf(errors)));
            return false;
        }

        /** Rows after the cutoff are not migrated; the system registers them from the next day. */
        boolean afterCutoff(String sheet, LocalDate date) {
            if (date != null && date.isAfter(cutoffDate)) {
                counts.get(sheet).afterCutoff++;
                return true;
            }
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MigrationResponse preview(String recaudacionesName, byte[] recaudaciones, String pedidosName, byte[] pedidos,
                                     LocalDate cutoffDate) {
        ensureNotDone();
        return toResponse(analyze(recaudacionesName, recaudaciones, pedidosName, pedidos, cutoffDate), false);
    }

    @Override
    public MigrationResponse importHistory(String recaudacionesName, byte[] recaudaciones, String pedidosName,
                                           byte[] pedidos, LocalDate cutoffDate, String username) {
        ensureNotDone();
        Analysis analysis = analyze(recaudacionesName, recaudaciones, pedidosName, pedidos, cutoffDate);
        if (!analysis.rejected.isEmpty()) {
            throw new IllegalStateException(HAS_REJECTED);
        }
        save(analysis, username);
        return toResponse(analysis, true);
    }

    private void ensureNotDone() {
        if (historicalImportRepository.count() > 0) {
            throw new IllegalStateException(ALREADY_DONE);
        }
    }

    // --- Reading ---

    private Analysis analyze(String recaudacionesName, byte[] recaudaciones, String pedidosName, byte[] pedidos,
                             LocalDate cutoffDate) {
        if (cutoffDate == null) {
            throw new IllegalArgumentException("Falta la fecha de corte.");
        }
        if (cutoffDate.isAfter(LocalDate.now(STORE_ZONE))) {
            throw new IllegalArgumentException("La fecha de corte no puede ser posterior a hoy.");
        }
        MigrationWorkbook workbook = new MigrationWorkbook(
            List.of(recaudacionesName == null ? "" : recaudacionesName, pedidosName == null ? "" : pedidosName),
            Arrays.asList(recaudaciones, pedidos));

        List<String> missing = new ArrayList<>();
        Map<String, Sheet> sheets = new LinkedHashMap<>();
        for (String name : List.of(VENTAS, TRANSACC, CIERRE, CONSUMOS, PEDIDOS, DETALLE)) {
            Sheet sheet = workbook.sheet(name);
            if (sheet == null) {
                missing.add(name);
            } else {
                sheets.put(name, sheet);
            }
        }
        Map<String, Map<String, Integer>> columns = new HashMap<>();
        Map<String, List<Column>> definitions = Map.of(VENTAS, SALE_COLUMNS, TRANSACC, MOVEMENT_COLUMNS,
            CIERRE, CLOSING_COLUMNS, CONSUMOS, CONSUMPTION_COLUMNS, PEDIDOS, ORDER_COLUMNS, DETALLE, DETAIL_COLUMNS);
        sheets.forEach((name, sheet) -> columns.put(name, MigrationWorkbook.columns(sheet, definitions.get(name), missing)));
        if (sheets.containsKey(CIERRE) && !columns.get(CIERRE).containsKey("date")) {
            columns.get(CIERRE).put("date", 0);
        }
        Sheet productsSheet = workbook.sheet(PRODUCTOS);
        Map<String, Integer> productColumns = productsSheet == null ? null
            : MigrationWorkbook.columns(productsSheet, PRODUCT_COLUMNS, missing);
        if (!missing.isEmpty()) {
            throw new FileRejected("Faltan las hojas o columnas: " + String.join(", ", missing) + ".");
        }

        Analysis analysis = new Analysis(cutoffDate);
        loadReferences(analysis);
        if (productsSheet != null) {
            readSaleUnits(analysis, rows(productsSheet, productColumns, List.of("code")));
        }
        analyzeSales(analysis, rows(sheets.get(VENTAS), columns.get(VENTAS), List.of("date", "code", "name", "total")));
        analyzeMovements(analysis, rows(sheets.get(TRANSACC), columns.get(TRANSACC),
            List.of("type", "date", "description", "amount")));
        analyzeClosings(analysis, rows(sheets.get(CIERRE), columns.get(CIERRE), List.of("date", "opening", "closing")));
        analyzeConsumptions(analysis, rows(sheets.get(CONSUMOS), columns.get(CONSUMOS),
            List.of("date", "code", "qty", "name")));
        analyzePurchases(analysis,
            rows(sheets.get(PEDIDOS), columns.get(PEDIDOS), List.of("order", "supplier", "amount", "name")),
            rows(sheets.get(DETALLE), columns.get(DETALLE), List.of("order", "code", "total")));
        return analysis;
    }

    private void loadReferences(Analysis analysis) {
        for (Product product : productRepository.findAll()) {
            if (product.getStatus() == ProductStatus.ELIMINADO) {
                continue;
            }
            if (product.getCode() != null) {
                analysis.products.put(key(product.getCode()), product);
            }
            if (product.getSpecialType() == ProductSpecialType.UNIDENTIFIED) {
                analysis.unidentified = product;
            }
        }
        for (FinancialAccount account : financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc()) {
            analysis.accounts.put(normalize(account.getName()), account);
            if (Boolean.TRUE.equals(account.getIsCashAccount())) {
                analysis.cashAccount = account;
            }
            if (Boolean.TRUE.equals(account.getIsQrAccount())) {
                analysis.qrAccount = account;
            }
        }
        for (FinancialCategory category : financialCategoryRepository.findByDeletedAtIsNullOrderByNameAsc()) {
            analysis.categories.computeIfAbsent(normalize(category.getName()), k -> new ArrayList<>()).add(category);
        }
        // The spreadsheet says Venta; the system's sales category may have another name (Ventas)
        financialCategoryRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .filter(c -> Boolean.TRUE.equals(c.getIsSalesDefault()))
            .findFirst()
            .ifPresent(sales -> analysis.categories.putIfAbsent("venta", new ArrayList<>(List.of(sales))));
        for (Supplier supplier : supplierRepository.findByStatusNotOrderByNameAsc(SupplierStatus.ELIMINADO)) {
            analysis.suppliers.put(normalize(supplier.getName()), supplier);
        }
    }

    /** CAT-08: how the spreadsheet counts the products sold by weight, from «Se vende por» of Productos. */
    private static void readSaleUnits(Analysis analysis, List<SheetRow> rows) {
        for (SheetRow row : rows) {
            SaleUnitFactor unit = ProductImportServiceImpl.saleUnit(row.text("saleUnit"));
            if (unit != null) {
                analysis.saleUnits.putIfAbsent(key(row.text("code")), unit);
            }
        }
    }

    // --- Ventas ---

    private void analyzeSales(Analysis analysis, List<SheetRow> rows) {
        for (SheetRow row : rows) {
            analysis.counts.get(VENTAS).read++;
            List<String> errors = new ArrayList<>();
            LocalDate date = requiredDate(row, "date", "Fecha", errors);
            if (analysis.afterCutoff(VENTAS, date)) {
                continue;
            }
            BigDecimal quantity = positive(row, "qty", "Cant", "la cantidad", errors);
            BigDecimal total = money(positive(row, "total", "Total", "el total", errors));
            BigDecimal cost = number(row.cell("cost"), "Costo unitario", errors);
            String code = row.text("code");
            String name = row.text("name");
            String comment = row.text("comment");
            String normalizedComment = normalize(comment);
            // P-39: «precio costo» goes to the at-cost sale of the day; gifts and breakages are at-cost sales too, marked
            // in the spreadsheet with «precio costo», so one without it only gets a notice
            boolean giftOrBreakage = normalizedComment.startsWith("regalo") || normalizedComment.contains("rompi");
            boolean atCost = normalizedComment.contains("precio costo");

            if (!errors.isEmpty()) {
                analysis.accept(VENTAS, row, errors);
                continue;
            }

            Product product = code.isEmpty() ? analysis.unidentified : null;
            ResolvedLine resolved = null;
            if (code.isEmpty() && product == null) {
                errors.add("Falta crear el «Producto sin identificar» en Productos (POS-12).");
            } else if (!code.isEmpty()) {
                resolved = resolve(analysis, code, quantity, true, errors);
                product = resolved != null ? resolved.product() : null;
            }
            if (product == null) {
                analysis.accept(VENTAS, row, errors);
                continue;
            }

            SaleItem item;
            if (product.getSpecialType() != null) {
                if (atCost) {
                    errors.add("Una venta a precio costo necesita el código de un producto del catálogo.");
                }
                if (name.isEmpty()) {
                    errors.add("Falta la descripción.");
                }
                item = errors.isEmpty() && quantityFits(quantity, code.isEmpty() ? name : code, errors)
                    ? freeLine(product, name, quantity.setScale(3, RoundingMode.UNNECESSARY), total) : null;
            } else {
                if (cost == null) {
                    errors.add("Falta el costo unitario.");
                } else if (cost.signum() < 0) {
                    errors.add("Costo unitario no puede ser negativo.");
                }
                item = errors.isEmpty() ? productLine(resolved, total, cost) : null;
            }
            if (!analysis.accept(VENTAS, row, errors)) {
                continue;
            }
            if (giftOrBreakage && !atCost) {
                analysis.notices.add(new RowMessage(VENTAS, row.rowNumber(), List.of("«" + comment + "» parece un regalo "
                    + "o una rotura: entra como venta normal. Si se pagó al costo, agregar «precio costo» al comentario (P-39).")));
            }
            SaleType type = atCost ? SaleType.AL_COSTO : SaleType.NORMAL;
            analysis.sales.computeIfAbsent(new SaleKey(date, type), k -> new ArrayList<>()).add(item);
            addSaleTotals(analysis, date, type, item);
        }
    }

    /**
     * POS-11: line of a special product with the name of the row; closed as unidentified for good (POS-12, the rows
     * were already reviewed one by one) or owed to its owner (POS-13).
     */
    private static SaleItem freeLine(Product product, String name, BigDecimal quantity, BigDecimal total) {
        boolean thirdParty = product.getSpecialType() == ProductSpecialType.THIRD_PARTY;
        String freeName = truncate(name, 150);
        BigDecimal unitPrice = total.divide(quantity, 2, RoundingMode.HALF_UP);
        return SaleItem.builder()
            .product(product)
            .productName(freeName)
            .productCode(product.getCode())
            .unitPrice(unitPrice)
            .unitCost(thirdParty ? unitPrice : ZERO)
            .discountPerUnit(ZERO)
            .finalUnitPrice(unitPrice)
            .quantity(quantity)
            .subtotal(total)
            .freeLineType(product.getSpecialType())
            .freeName(freeName)
            .assignmentStatus(thirdParty ? null : SaleItemAssignmentStatus.SIN_IDENTIFICAR)
            .creditor(thirdParty ? product.getOwnerCreditor() : null)
            .build();
    }

    /** A catalog line: price = total ÷ quantity and the cost of the row, both per unit or per kilo. */
    private static SaleItem productLine(ResolvedLine resolved, BigDecimal total, BigDecimal cost) {
        Product product = resolved.product();
        ProductPresentation presentation = resolved.presentation();
        BigDecimal unitPrice = total.divide(resolved.quantity(), 2, RoundingMode.HALF_UP);
        return SaleItem.builder()
            .product(product)
            .productName(product.getName())
            .productCode(product.getCode())
            .unitPrice(unitPrice)
            .unitCost(resolved.unitCost(cost))
            .discountPerUnit(ZERO)
            .finalUnitPrice(unitPrice)
            .quantity(resolved.quantity())
            .presentation(presentation)
            .presentationName(presentation != null ? presentation.getName() : null)
            .presentationWeightKg(presentation != null ? presentation.getWeightKg() : null)
            .weightKg(resolved.weightKg())
            .subtotal(total)
            .build();
    }

    private static void addSaleTotals(Analysis analysis, LocalDate date, SaleType type, SaleItem item) {
        MonthTotalsDraft month = analysis.month(date);
        BigDecimal subtotal = item.getSubtotal();
        if (type == SaleType.AL_COSTO) {
            month.atCost = month.atCost.add(subtotal);
        } else if (item.getFreeLineType() == ProductSpecialType.THIRD_PARTY) {
            month.thirdParty = month.thirdParty.add(subtotal);
            analysis.thirdPartyDebts.merge(item.getCreditor().getName(), subtotal, BigDecimal::add);
        } else if (item.getFreeLineType() == ProductSpecialType.UNIDENTIFIED) {
            month.sales = month.sales.add(subtotal);
            month.unidentified = month.unidentified.add(subtotal);
            analysis.unidentifiedLines++;
            analysis.unidentifiedTotal = analysis.unidentifiedTotal.add(subtotal);
        } else {
            BigDecimal quantity = item.getWeightKg() != null ? item.getWeightKg() : item.getQuantity();
            BigDecimal cost = quantity.multiply(item.getUnitCost()).setScale(2, RoundingMode.HALF_UP);
            month.sales = month.sales.add(subtotal);
            month.profit = month.profit.add(subtotal.subtract(cost));
        }
    }

    // --- Consumos ---

    private void analyzeConsumptions(Analysis analysis, List<SheetRow> rows) {
        for (SheetRow row : rows) {
            analysis.counts.get(CONSUMOS).read++;
            List<String> errors = new ArrayList<>();
            LocalDate date = requiredDate(row, "date", "Fecha", errors);
            if (analysis.afterCutoff(CONSUMOS, date)) {
                continue;
            }
            BigDecimal quantity = positive(row, "qty", "Cantidad", "la cantidad", errors);
            BigDecimal cost = number(row.cell("cost"), "Costo unitario", errors);
            if (cost == null && row.blank("cost")) {
                errors.add("Falta el costo unitario.");
            } else if (cost != null && cost.signum() < 0) {
                errors.add("Costo unitario no puede ser negativo.");
            }
            ConsumptionItem item = errors.isEmpty() ? consumptionItem(analysis, row.text("code"), quantity, cost, errors) : null;
            if (analysis.accept(CONSUMOS, row, errors)) {
                analysis.consumptions.add(new ConsumptionDraft(date, truncate(emptyToNull(row.text("for")), 150),
                    truncate(emptyToNull(row.text("comment")), 500), item));
                analysis.month(date).consumptions = analysis.month(date).consumptions.add(item.getSubtotal());
            }
        }
    }

    /** CON-01 at the cost of the row: subtotal = quantity × cost of the spreadsheet. */
    private ConsumptionItem consumptionItem(Analysis analysis, String code, BigDecimal quantity, BigDecimal cost,
                                           List<String> errors) {
        if (code.isEmpty()) {
            errors.add("Falta el código.");
            return null;
        }
        if (cost == null) {
            errors.add("Falta el costo unitario.");
            return null;
        }
        ResolvedLine resolved = resolve(analysis, code, quantity, true, errors);
        if (resolved == null) {
            return null;
        }
        if (resolved.product().getSpecialType() != null) {
            errors.add("El producto " + code + " es especial: no va en consumos.");
            return null;
        }
        ProductPresentation presentation = resolved.presentation();
        return ConsumptionItem.builder()
            .product(resolved.product())
            .productName(resolved.product().getName())
            .productCode(resolved.product().getCode())
            .quantity(resolved.quantity())
            .unitCost(resolved.unitCost(cost))
            .presentation(presentation)
            .presentationName(presentation != null ? presentation.getName() : null)
            .presentationWeightKg(presentation != null ? presentation.getWeightKg() : null)
            .weightKg(resolved.weightKg())
            .subtotal(quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP))
            .build();
    }

    // --- Transacc. ---

    private void analyzeMovements(Analysis analysis, List<SheetRow> rows) {
        for (SheetRow row : rows) {
            analysis.counts.get(TRANSACC).read++;
            List<String> errors = new ArrayList<>();
            LocalDate date = requiredDate(row, "date", "Fecha", errors);
            if (analysis.afterCutoff(TRANSACC, date)) {
                continue;
            }
            String type = normalize(row.text("type"));
            if (!List.of("ingreso", "gasto", "retiro", "ajuste", "transferencia").contains(type)) {
                errors.add(row.blank("type") ? "Falta el tipo."
                    : "Tipo debe ser Ingreso, Gasto, Retiro, Ajuste o Transferencia: '" + row.text("type") + "'.");
            }
            BigDecimal amount = number(row.cell("amount"), "Monto", errors);
            if (amount == null && row.blank("amount")) {
                errors.add("Falta el monto.");
            } else if (amount != null && money(amount).signum() == 0) {
                errors.add("El monto no puede ser 0.");
            }
            FinancialAccount account = account(analysis, row.text("account"), "la cuenta", errors);
            String description = truncate(row.blank("description") ? row.text("category") : row.text("description"), 255);
            String notes = truncate(emptyToNull(row.text("comment")), 500);

            FinancialTransaction.FinancialTransactionBuilder movement = FinancialTransaction.builder()
                .description(description.isEmpty() ? "Movimiento histórico" : description)
                .notes(notes)
                .status(FinancialTransactionStatus.ACTIVO)
                // A row without a date is rejected below
                .transactionDate(date != null ? instant(date) : null);
            if (type.equals("transferencia")) {
                FinancialAccount destination = account(analysis, row.text("destination"), "la cuenta destino", errors);
                if (account != null && account == destination) {
                    errors.add("La cuenta y la cuenta destino son la misma.");
                }
                if (!analysis.accept(TRANSACC, row, errors)) {
                    continue;
                }
                // A negative transfer goes the other way
                boolean reverse = amount.signum() < 0;
                FinancialAccount from = reverse ? destination : account;
                FinancialAccount to = reverse ? account : destination;
                analysis.transactions.add(movement.type(FinancialTransactionType.TRANSFERENCIA)
                    .sourceAccount(from).destinationAccount(to).amount(money(amount.abs())).build());
                analysis.movement(date, "Transferencia " + from.getName() + " → " + to.getName(), money(amount.abs()));
                continue;
            }

            // The sign says the direction, as the account columns of the spreadsheet add it
            boolean income = amount != null && amount.signum() > 0;
            String categoryName = row.blank("category") && type.equals("ajuste") ? "Ajuste" : row.text("category");
            FinancialCategory category = null;
            if (categoryName.isEmpty()) {
                errors.add("Falta la categoría.");
            } else {
                category = category(analysis, categoryName, income ? FinancialCategoryType.INGRESO : FinancialCategoryType.GASTO);
                if (category == null) {
                    errors.add("La categoría " + categoryName + " no existe en Finanzas.");
                }
            }
            if (!analysis.accept(TRANSACC, row, errors)) {
                continue;
            }
            BigDecimal value = money(amount.abs());
            analysis.transactions.add(movement
                .type(income ? FinancialTransactionType.INGRESO_MANUAL : FinancialTransactionType.GASTO_MANUAL)
                .sourceAccount(account).category(category).amount(value).build());
            analysis.movement(date, category.getName(), income ? value : value.negate());
        }
    }

    private static FinancialAccount account(Analysis analysis, String name, String label, List<String> errors) {
        if (name.isEmpty()) {
            errors.add("Falta " + label + ".");
            return null;
        }
        FinancialAccount account = analysis.accounts.get(normalize(name));
        if (account == null) {
            errors.add("La cuenta " + name + " no existe en Finanzas.");
        }
        return account;
    }

    /** By name; the one of the movement's direction when both exist (Ajuste is an income and an expense). */
    private static FinancialCategory category(Analysis analysis, String name, FinancialCategoryType type) {
        List<FinancialCategory> sameName = analysis.categories.get(normalize(name));
        if (sameName == null) {
            return null;
        }
        return sameName.stream().filter(c -> c.getType() == type).findFirst().orElse(sameName.get(0));
    }

    // --- Cierre de Caja ---

    private void analyzeClosings(Analysis analysis, List<SheetRow> rows) {
        Map<LocalDate, Integer> rowByDate = new HashMap<>();
        for (SheetRow row : rows) {
            analysis.counts.get(CIERRE).read++;
            List<String> errors = new ArrayList<>();
            LocalDate date = requiredDate(row, "date", "Fecha", errors);
            if (analysis.afterCutoff(CIERRE, date)) {
                continue;
            }
            if (date != null) {
                Integer first = rowByDate.putIfAbsent(date, row.rowNumber());
                if (first != null) {
                    errors.add("El día ya está en la fila " + first + ".");
                }
            }
            BigDecimal opening = money(number(row.cell("opening"), "Caja Inicial", errors));
            BigDecimal closing = money(number(row.cell("closing"), "Caja final", errors));
            if (row.blank("opening")) {
                errors.add("Falta la caja inicial.");
            }
            if (row.blank("closing")) {
                errors.add("Falta la caja final.");
            }
            Map<String, BigDecimal> amounts = new HashMap<>();
            for (Column column : CLOSING_COLUMNS) {
                if (CLOSING_AMOUNTS.contains(column.key())) {
                    BigDecimal value = money(number(row.cell(column.key()), column.titles()[0], errors));
                    amounts.put(column.key(), value == null ? ZERO : value);
                }
            }
            if (!analysis.accept(CIERRE, row, errors)) {
                continue;
            }
            // CAJ-09 does not apply: the surplus or shortage is already in Transacc. if the spreadsheet has it
            analysis.sessions.add(CashSession.builder()
                .status(CashSessionStatus.CERRADA)
                .openingAmount(opening)
                .openedAt(date.atTime(LocalTime.of(9, 0)).atZone(STORE_ZONE).toInstant())
                .closingAmount(closing)
                .closingComment(truncate(emptyToNull(row.text("comment")), 500))
                .closedAt(date.atTime(LocalTime.of(21, 0)).atZone(STORE_ZONE).toInstant())
                .totalSalesCash(amounts.get("cash"))
                .totalSalesQr(amounts.get("qr"))
                .totalSales(amounts.get("total"))
                .expectedCash(amounts.get("result"))
                .difference(amounts.get("surplus"))
                .historicalOtherIncome(amounts.get("otherIncome"))
                .historicalExpenses(amounts.get("expenses"))
                .historicalOtherOutflow(amounts.get("otherOutflow"))
                .historicalInternalOutflow(amounts.get("internalOutflow"))
                .build());
        }
    }

    // --- Pedidos + Detalle Pedidos ---

    private void analyzePurchases(Analysis analysis, List<SheetRow> orderRows, List<SheetRow> detailRows) {
        // Order numbers first, so a detail line can point to an order in any row
        Map<Integer, SheetRow> orderByNumber = new HashMap<>();
        Map<Integer, List<String>> orderErrors = new HashMap<>();
        for (SheetRow row : orderRows) {
            List<String> errors = new ArrayList<>();
            Integer number = orderNumber(row, errors);
            if (number != null) {
                SheetRow first = orderByNumber.putIfAbsent(number, row);
                if (first != null) {
                    errors.add("El pedido " + number + " ya está en la fila " + first.rowNumber() + ".");
                }
            }
            orderErrors.put(row.rowNumber(), errors);
        }

        Map<Integer, List<PurchaseItem>> linesByOrder = new HashMap<>();
        Map<Integer, LocalDate> dateByOrder = new HashMap<>();
        Map<Integer, String> supplierByOrder = new HashMap<>();
        for (SheetRow row : detailRows) {
            analysis.counts.get(DETALLE).read++;
            List<String> errors = new ArrayList<>();
            Integer number = orderNumber(row, errors);
            LocalDate date = requiredDate(row, "date", "Fecha", errors);
            if (analysis.afterCutoff(DETALLE, date)) {
                continue;
            }
            if (number != null && !orderByNumber.containsKey(number)) {
                errors.add("El pedido " + number + " no está en la hoja Pedidos.");
            }
            BigDecimal quantity = positive(row, "qty", "Cant", "la cantidad", errors);
            BigDecimal cost = number(row.cell("cost"), "Precio unitario", errors);
            if (cost == null && row.blank("cost")) {
                errors.add("Falta el precio unitario.");
            } else if (cost != null && cost.signum() < 0) {
                errors.add("Precio unitario no puede ser negativo.");
            }
            BigDecimal total = money(number(row.cell("total"), "Total", errors));
            String code = row.text("code");
            ResolvedLine resolved = null;
            if (code.isEmpty()) {
                errors.add("Falta el código del producto.");
            } else if (errors.isEmpty()) {
                resolved = resolve(analysis, code, quantity, false, errors);
                if (resolved != null && resolved.product().getSpecialType() != null) {
                    errors.add("El producto " + code + " es especial: no va en pedidos.");
                }
            }
            if (!analysis.accept(DETALLE, row, errors)) {
                continue;
            }
            BigDecimal unitCost = resolved.unitCost(cost);
            linesByOrder.computeIfAbsent(number, n -> new ArrayList<>()).add(PurchaseItem.builder()
                .product(resolved.product())
                .productName(resolved.product().getName())
                .productCode(resolved.product().getCode())
                .quantity(resolved.quantity())
                .unitCost(unitCost)
                .subtotal(total != null ? total : quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP))
                .previousUnitCost(unitCost)
                .costUpdated(false)
                .build());
            dateByOrder.putIfAbsent(number, date);
            if (!row.blank("supplier")) {
                supplierByOrder.putIfAbsent(number, row.text("supplier"));
            }
        }

        for (SheetRow row : orderRows) {
            analysis.counts.get(PEDIDOS).read++;
            List<String> errors = orderErrors.get(row.rowNumber());
            Integer number = orderNumber(row, new ArrayList<>());
            LocalDate payDate = date(row.cell("payDate"), "Fecha pago", errors);
            LocalDate deliveryDate = date(row.cell("deliveryDate"), "Fecha Entrega", errors);
            // The order date: its first detail line, else the delivery or the payment (the sheet has no order date)
            LocalDate purchaseDate = number != null && dateByOrder.containsKey(number) ? dateByOrder.get(number)
                : deliveryDate != null ? deliveryDate : payDate;
            if (purchaseDate == null && errors.isEmpty()) {
                errors.add("Falta la fecha: el pedido no tiene líneas, Fecha Entrega ni Fecha pago.");
            }
            if (analysis.afterCutoff(PEDIDOS, purchaseDate)) {
                continue;
            }

            String supplierName = !row.blank("supplier") ? row.text("supplier")
                : number != null ? supplierByOrder.getOrDefault(number, "") : "";
            Supplier supplier = null;
            if (supplierName.isEmpty()) {
                errors.add("Falta el proveedor.");
            } else {
                supplier = analysis.suppliers.get(normalize(supplierName));
                if (supplier == null) {
                    errors.add("El proveedor " + supplierName + " no existe.");
                }
            }
            BigDecimal itemsTotal = money(positive(row, "amount", "Monto", "el monto", errors));
            BigDecimal shipping = money(number(row.cell("shipping"), "Monto Envio", errors));
            if (shipping != null && shipping.signum() < 0) {
                errors.add("Monto Envio no puede ser negativo.");
            }
            shipping = shipping == null ? ZERO : shipping;

            String status = normalize(row.text("payStatus"));
            boolean consignment = status.equals("consignacion");
            BigDecimal paid = ZERO;
            switch (status) {
                case "pagado" -> paid = itemsTotal;
                case "a cuenta" -> {
                    BigDecimal partial = money(number(row.cell("paid"), "Monto pagado", errors));
                    if (partial == null) {
                        errors.add("Falta el monto pagado del pedido a cuenta.");
                    } else if (itemsTotal != null && (partial.signum() <= 0 || partial.compareTo(itemsTotal) >= 0)) {
                        errors.add("El monto pagado debe ser mayor a 0 y menor al Monto.");
                    } else {
                        paid = partial;
                    }
                }
                case "por pagar", "consignacion" -> { }
                default -> errors.add(row.blank("payStatus") ? "Falta el estado de pago."
                    : "Estado Pago debe ser Pagado, A Cuenta, Por Pagar o Consignacion: '" + row.text("payStatus") + "'.");
            }
            FinancialAccount payAccount = switch (normalize(row.text("payType"))) {
                case "caja" -> analysis.cashAccount;
                case "qr" -> analysis.qrAccount;
                case "", "otro" -> null;
                default -> {
                    errors.add("Tipo Pago debe ser Caja, QR u Otro: '" + row.text("payType") + "'.");
                    yield null;
                }
            };
            DeliveryType deliveryType = switch (normalize(row.text("deliveryType"))) {
                case "en puerta" -> DeliveryType.EN_PUERTA;
                case "terminal" -> DeliveryType.TERMINAL;
                case "", "en tienda" -> DeliveryType.EN_TIENDA;
                default -> {
                    errors.add("Tipo Entrega debe ser En puerta, Terminal o En tienda: '" + row.text("deliveryType") + "'.");
                    yield null;
                }
            };
            DeliveryStatus deliveryStatus = switch (normalize(row.text("deliveryStatus"))) {
                case "entregado" -> DeliveryStatus.ENTREGADO;
                case "", "en camino" -> DeliveryStatus.PENDIENTE;
                default -> {
                    errors.add("Estado de Entrega debe ser Entregado o En camino: '" + row.text("deliveryStatus") + "'.");
                    yield null;
                }
            };
            if (!analysis.accept(PEDIDOS, row, errors)) {
                continue;
            }

            List<PurchaseItem> lines = linesByOrder.getOrDefault(number, List.of());
            BigDecimal linesTotal = lines.stream().map(PurchaseItem::getSubtotal).reduce(ZERO, BigDecimal::add);
            if (!lines.isEmpty() && linesTotal.compareTo(itemsTotal) != 0) {
                analysis.notices.add(new RowMessage(PEDIDOS, row.rowNumber(), List.of("La suma de las líneas ("
                    + linesTotal.toPlainString() + ") no coincide con el Monto (" + itemsTotal.toPlainString()
                    + "): el pedido queda con el Monto.")));
            }
            String comment = String.join(" · ", List.of(row.text("name"), row.text("comment")).stream()
                .filter(text -> !text.isEmpty()).toList());
            Purchase purchase = Purchase.builder()
                .purchaseNumber(formatHistoricalPurchaseNumber(number))
                .supplier(supplier)
                .purchaseDate(purchaseDate)
                .status(PurchaseStatus.REGISTRADO)
                .itemsTotal(itemsTotal)
                .shippingCost(shipping)
                .totalAmount(itemsTotal.add(shipping))
                .paidAmount(paid)
                .consignment(consignment)
                .comment(truncate(emptyToNull(comment), 500))
                .deliveryType(deliveryType)
                .deliveryStatus(deliveryStatus)
                .deliveryDate(deliveryStatus == DeliveryStatus.ENTREGADO
                    ? (deliveryDate != null ? deliveryDate : purchaseDate) : null)
                .build();
            lines.forEach(line -> line.setPurchase(purchase));
            purchase.getItems().addAll(lines);
            if (paid.signum() > 0) {
                // P-43: the money is in Transacc. (Pedido); the payment only gives the payment status (COM-04)
                purchase.getPayments().add(PurchasePayment.builder()
                    .purchase(purchase)
                    .account(payAccount)
                    .amount(paid)
                    .paymentDate(instant(payDate != null ? payDate : purchaseDate))
                    .status(PurchasePaymentRecordStatus.ACTIVO)
                    .build());
            }
            analysis.purchases.add(purchase);
        }
    }

    private static Integer orderNumber(SheetRow row, List<String> errors) {
        BigDecimal value = number(row.cell("order"), "El número de pedido", errors);
        if (value == null) {
            if (row.blank("order")) {
                errors.add("Falta el número de pedido.");
            }
            return null;
        }
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
            errors.add("El número de pedido debe ser un entero mayor a 0: '" + row.text("order") + "'.");
            return null;
        }
        return value.intValueExact();
    }

    static String formatHistoricalPurchaseNumber(int orderNumber) {
        return String.format("PED-H%05d", orderNumber);
    }

    // --- Lines ---

    /**
     * The product of a code with the quantity in the system's unit: kilos for products sold by weight (the
     * spreadsheet counts them as «Se vende por» of Productos says, CAT-08) and packs for the pound codes, which
     * are presentations of their base product (CAT-11).
     */
    private static ResolvedLine resolve(Analysis analysis, String code, BigDecimal quantity, boolean allowPresentation,
                                        List<String> errors) {
        Product product = analysis.products.get(key(code));
        if (product == null) {
            PoundPresentation pound = poundOf(code);
            Product base = pound == null ? null
                : analysis.products.get(key(code.substring(0, code.length() - pound.suffix().length())));
            if (base == null) {
                errors.add("El código " + code + " no existe en el catálogo.");
                return null;
            }
            if (!allowPresentation) {
                errors.add("El código " + code + " es una presentación: el pedido se registra en " + base.getCode() + ".");
                return null;
            }
            ProductPresentation presentation = base.getPresentations().stream()
                .filter(p -> p.getWeightKg().compareTo(pound.weightKg()) == 0)
                .findFirst()
                .orElse(null);
            if (presentation == null) {
                errors.add("El producto " + base.getCode() + " no tiene la presentación " + pound.name() + ".");
                return null;
            }
            if (!quantityFits(quantity, code, errors)) {
                return null;
            }
            BigDecimal weight = quantity.multiply(presentation.getWeightKg()).setScale(3, RoundingMode.HALF_UP);
            // The cost of the row is per pack; the line keeps it per kilo, as the POS does
            return new ResolvedLine(base, presentation, quantity, weight,
                BigDecimal.ONE.divide(presentation.getWeightKg(), 10, RoundingMode.HALF_UP));
        }
        if (product.getSaleUnit() == SaleUnit.KILOGRAM && product.getSpecialType() == null) {
            SaleUnitFactor unit = analysis.saleUnits.get(key(code));
            if (unit == null || unit.saleUnit() != SaleUnit.KILOGRAM) {
                errors.add("El producto " + code + " se vende por kilo: marcar «Se vende por» en Productos.");
                return null;
            }
            BigDecimal kilos = quantity.divide(unit.factor(), 10, RoundingMode.HALF_UP).stripTrailingZeros();
            if (kilos.scale() > 3) {
                errors.add("La cantidad de " + code + " en kilos tiene más de 3 decimales: '"
                    + quantity.stripTrailingZeros().toPlainString() + "'.");
                return null;
            }
            return new ResolvedLine(product, null, kilos.setScale(3, RoundingMode.UNNECESSARY), null, unit.factor());
        }
        if (!quantityFits(quantity, code, errors)) {
            return null;
        }
        return new ResolvedLine(product, null, quantity.setScale(3, RoundingMode.UNNECESSARY), null, BigDecimal.ONE);
    }

    private static boolean quantityFits(BigDecimal quantity, String code, List<String> errors) {
        if (quantity.stripTrailingZeros().scale() > 3) {
            errors.add("La cantidad de " + code + " tiene más de 3 decimales.");
            return false;
        }
        return true;
    }

    private static PoundPresentation poundOf(String code) {
        String upper = code.toUpperCase(Locale.ROOT);
        return ProductImportServiceImpl.POUND_PRESENTATIONS.stream()
            .filter(p -> upper.endsWith(p.suffix()) && upper.length() > p.suffix().length())
            .findFirst()
            .orElse(null);
    }

    // --- Values ---

    private static LocalDate requiredDate(SheetRow row, String key, String title, List<String> errors) {
        LocalDate date = date(row.cell(key), title, errors);
        if (date == null && row.blank(key)) {
            errors.add("Falta la fecha.");
        }
        return date;
    }

    private static BigDecimal positive(SheetRow row, String key, String title, String missing, List<String> errors) {
        BigDecimal value = number(row.cell(key), title, errors);
        if (value == null) {
            if (row.blank(key)) {
                errors.add("Falta " + missing + ".");
            }
            return null;
        }
        if (value.signum() <= 0) {
            errors.add(title + " debe ser mayor a 0.");
            return null;
        }
        return value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Instant instant(LocalDate date) {
        return date.atTime(LocalTime.NOON).atZone(STORE_ZONE).toInstant();
    }

    private static String key(String code) {
        return clean(code).toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    // --- Saving ---

    private void save(Analysis analysis, String username) {
        Instant now = Instant.now();
        UUID importId = historicalImportRepository.save(HistoricalImport.builder()
            .cutoffDate(analysis.cutoffDate).importedBy(username).importedAt(now).build()).getId();

        analysis.sales.forEach((key, items) -> {
            BigDecimal total = items.stream().map(SaleItem::getSubtotal).reduce(ZERO, BigDecimal::add);
            Sale sale = Sale.builder()
                .saleNumber(String.format("HIS-%06d", saleRepository.nextHistoricalSaleNumber()))
                .saleType(key.type())
                .status(SaleStatus.COMPLETADA)
                .subtotalAmount(total)
                .totalAmount(total)
                .paymentMethod(PaymentMethod.HISTORICO)
                .description((key.type() == SaleType.AL_COSTO ? "Ventas al costo" : "Ventas")
                    + " del " + key.date().format(DAY) + " (planilla)")
                .createdBy(username)
                .createdAt(instant(key.date()))
                .historicalImportId(importId)
                .build();
            items.forEach(item -> {
                item.setSale(sale);
                // Who and when closed the unidentified lines (POS-12): the import itself
                if (item.getAssignmentStatus() == SaleItemAssignmentStatus.SIN_IDENTIFICAR) {
                    item.setAssignedBy(username);
                    item.setAssignedAt(now);
                }
            });
            sale.getItems().addAll(items);
            saleRepository.save(sale);
        });

        for (ConsumptionDraft draft : analysis.consumptions) {
            Consumption consumption = Consumption.builder()
                .consumptionNumber(ConsumptionServiceImpl.formatConsumptionNumber(consumptionRepository.nextConsumptionNumber()))
                .consumptionDate(draft.date())
                .consumedFor(draft.consumedFor())
                .comment(draft.comment())
                .totalAmount(draft.item().getSubtotal())
                .createdBy(username)
                .createdAt(now)
                .historicalImportId(importId)
                .build();
            draft.item().setConsumption(consumption);
            consumption.getItems().add(draft.item());
            consumptionRepository.save(consumption);
        }

        for (FinancialTransaction transaction : analysis.transactions) {
            transaction.setCreatedBy(username);
            transaction.setCreatedAt(now);
            transaction.setHistoricalImportId(importId);
        }
        financialTransactionRepository.saveAll(analysis.transactions);

        for (CashSession session : analysis.sessions) {
            session.setOpenedBy(username);
            session.setClosedBy(username);
            session.setHistoricalImportId(importId);
        }
        cashSessionRepository.saveAll(analysis.sessions);

        for (Purchase purchase : analysis.purchases) {
            purchase.setCreatedBy(username);
            purchase.setCreatedAt(now);
            purchase.setHistoricalImportId(importId);
            for (PurchasePayment payment : purchase.getPayments()) {
                payment.setCreatedBy(username);
                payment.setCreatedAt(now);
                payment.setHistoricalImportId(importId);
            }
        }
        purchaseRepository.saveAll(analysis.purchases);
    }

    private static MigrationResponse toResponse(Analysis analysis, boolean imported) {
        List<SheetCount> sheets = analysis.counts.entrySet().stream()
            .map(e -> new SheetCount(e.getKey(), e.getValue().read, e.getValue().toImport, e.getValue().rejected,
                e.getValue().afterCutoff))
            .toList();
        List<OwnerDebt> debts = analysis.thirdPartyDebts.entrySet().stream()
            .map(e -> new OwnerDebt(e.getKey(), e.getValue()))
            .toList();
        List<MonthTotals> months = analysis.months.entrySet().stream()
            .map(e -> new MonthTotals(e.getKey(), e.getValue().sales, e.getValue().profit, e.getValue().unidentified,
                e.getValue().atCost, e.getValue().thirdParty, e.getValue().consumptions))
            .toList();
        List<MovementTotal> movements = new ArrayList<>();
        analysis.movements.forEach((month, concepts) ->
            concepts.forEach((concept, amount) -> movements.add(new MovementTotal(month, concept, amount))));
        List<RowMessage> rejected = new ArrayList<>(analysis.rejected);
        rejected.sort(Comparator.comparing(RowMessage::sheet).thenComparingInt(RowMessage::rowNumber));
        return new MigrationResponse(analysis.cutoffDate, imported, sheets, rejected, List.copyOf(analysis.notices),
            analysis.unidentifiedLines, analysis.unidentifiedTotal, debts, months, movements);
    }
}
