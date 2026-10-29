package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.MigrationResponse;
import com.akhana.akhana_admin.dto.MigrationResponse.RowMessage;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.MigrationService;
import com.akhana.akhana_admin.service.SaleService;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FIN-11 against the real database: spreadsheets built in the test with every case of specs/migration.md, the
 * historical marks and the balances and cash proposal that ignore them. Rolled back after each test.
 */
@SpringBootTest
@Transactional
class MigrationIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 17);
    private static final LocalDate CUTOFF = LocalDate.of(2026, 10, 26);
    private static final LocalDate AFTER = CUTOFF.plusDays(1);

    @Autowired private MigrationService migrationService;
    @Autowired private SaleService saleService;
    @Autowired private CashSessionService cashSessionService;
    @Autowired private FinancialTransactionRepository financialTransactionRepository;
    @Autowired private JdbcTemplate jdbc;
    // JdbcTemplate does not flush Hibernate's pending inserts (the real import commits)
    @Autowired private EntityManager entityManager;

    private String tag;
    private String unit;
    private String nut;
    private String mamiCode;
    private String mamiName;
    private String supplier;
    private String accountA;
    private String accountB;
    private String expenseCategory;
    private UUID accountAId;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        unit = "IT" + tag + "-AAA";
        nut = "IT" + tag + "-NUE";
        mamiCode = "IT" + tag + "-MAMI";
        mamiName = "IT Mami " + tag;
        supplier = "IT Proveedor " + tag;
        accountA = "IT Cuenta A " + tag;
        accountB = "IT Cuenta B " + tag;
        expenseCategory = "IT Pedido " + tag;

        UUID category = jdbc.queryForObject(
            "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it') RETURNING id", UUID.class,
            "IT cat " + tag);
        UUID supplierId = jdbc.queryForObject(
            "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it') RETURNING id", UUID.class, supplier,
            "IT-" + tag);
        insertProduct(unit, "IT Producto " + tag, category, supplierId, "UNIT", null, null);
        UUID nutId = insertProduct(nut, "IT Nuez " + tag, category, supplierId, "KILOGRAM", null, null);
        jdbc.update("INSERT INTO product_presentations (product_id, name, weight_kg, created_by) VALUES (?, '1/2 libra', 0.227, 'it')",
            nutId);
        UUID mami = jdbc.queryForObject("INSERT INTO creditors (name, created_by) VALUES (?, 'it') RETURNING id",
            UUID.class, mamiName);
        insertProduct(mamiCode, "IT Producto de Mami " + tag, category, supplierId, "UNIT", "THIRD_PARTY", mami);
        Integer unidentified = jdbc.queryForObject(
            "SELECT COUNT(*) FROM products WHERE special_type = 'UNIDENTIFIED' AND status <> 'ELIMINADO'", Integer.class);
        if (unidentified == 0) {
            insertProduct("IT" + tag + "-SIN", "IT Producto sin identificar " + tag, category, supplierId, "UNIT",
                "UNIDENTIFIED", null);
        }
        accountAId = jdbc.queryForObject(
            "INSERT INTO financial_accounts (name, initial_balance, current_balance) VALUES (?, 500, 500) RETURNING id",
            UUID.class, accountA);
        jdbc.update("INSERT INTO financial_accounts (name, initial_balance, current_balance) VALUES (?, 0, 0)", accountB);
        jdbc.update("INSERT INTO financial_categories (name, type, color) VALUES (?, 'GASTO', '#000000')", expenseCategory);
    }

    private UUID insertProduct(String code, String name, UUID category, UUID supplierId, String saleUnit,
                               String specialType, UUID owner) {
        return jdbc.queryForObject(
            "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, "
                + "percentage_profit, sale_unit, special_type, owner_creditor_id, created_by) "
                + "VALUES (?, ?, ?, ?, 10, 15, 5, 50, ?, ?, ?, 'it') RETURNING id",
            UUID.class, code, name, category, supplierId, saleUnit, specialType, owner);
    }

    // --- Spreadsheets ---

    private static byte[] workbook(Map<String, List<Object[]>> sheets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("d/m/yyyy"));
            sheets.forEach((name, rows) -> {
                XSSFSheet sheet = workbook.createSheet(name);
                for (int r = 0; r < rows.size(); r++) {
                    XSSFRow row = sheet.createRow(r);
                    Object[] values = rows.get(r);
                    for (int c = 0; c < values.length; c++) {
                        if (values[c] instanceof Number number) {
                            row.createCell(c).setCellValue(number.doubleValue());
                        } else if (values[c] instanceof LocalDate date) {
                            var cell = row.createCell(c);
                            cell.setCellValue(date);
                            cell.setCellStyle(dateStyle);
                        } else if (values[c] != null) {
                            row.createCell(c).setCellValue((String) values[c]);
                        }
                    }
                }
            });
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Object[]> sheet(Object[] header, Object[]... rows) {
        List<Object[]> all = new ArrayList<>();
        all.add(header);
        all.addAll(Arrays.asList(rows));
        return all;
    }

    private static final Object[] VENTAS = {"Fecha", "Grupo", "Provedor", "Codigo", "Cant", "Descripción",
        "Precio unitario", "Total", "Comentarios", "Veri", " ", "Nombre", "Costo unitario", "Costo Total"};

    private Object[] sale(LocalDate date, String code, double qty, String name, double total, String comment, Double cost) {
        return new Object[]{date, null, null, code, qty, name, total / qty, total, comment, null, null, null, cost, null};
    }

    private Map<String, List<Object[]>> recaudaciones(List<Object[]> ventas, List<Object[]> transacc,
                                                      List<Object[]> productos) {
        Map<String, List<Object[]>> sheets = new LinkedHashMap<>();
        sheets.put("Cierre de Caja", sheet(
            new Object[]{" ", "Caja Inicial", "Otro Ingreso", "Total", "QR", "Efectivo", "Gastos Caja", "Otra salida",
                "Salida Interna", "Resultado", "Caja final", "Sobrante", "Comentarios"},
            new Object[]{DAY, 1000, null, 300, 100, 200, 50, 0, 0, 1150, 1155, 5, "Sobra 5"}));
        sheets.put("Transacc.", transacc);
        sheets.put("Consumos", sheet(
            new Object[]{"Fecha", "Cantidad", "Descripción", "Codigo", "Costo unitario", "Para quien"},
            new Object[]{DAY, 1, "Producto", unit, 10, "Ali"}));
        sheets.put("Productos", productos);
        sheets.put("Ventas", ventas);
        sheets.put("Detalle Pedidos", sheet(
            new Object[]{"Codigo", "Fecha", "Provedor", "Codigo", "Descricpion", "Cant", "Precio unitario", "Total"},
            new Object[]{3, DAY, supplier, unit, "Producto", 3, 15, 45}));
        return sheets;
    }

    private List<Object[]> transacc(Object[]... rows) {
        return sheet(new Object[]{"Tipo", "Mes", "Fecha", "Descripcion", "Monto", "Categoria", "Cuenta", "Cuenta Destino",
            "Comentarios"}, rows);
    }

    private List<Object[]> productos(String saleUnit) {
        return sheet(new Object[]{"Grupo", "Proveedor", "Nombre", "Codigo", "Costo", "Precio Venta", "Se vende por"},
            new Object[]{"Nutricion", supplier, "Nuez", nut, 0.13, 0.21, saleUnit});
    }

    private byte[] pedidos(Object[]... rows) {
        return workbook(Map.of("Pedidos", sheet(new Object[]{"COD", "Nombre", "Proveedor", "Monto", "Monto Envio",
            "Tipo Pago", "Estado Pago", "Fecha pago", "Monto pagado", "Tipo Entrega", "Estado de Entrega",
            "Fecha Entrega", "Comentarios"}, rows)));
    }

    private byte[] validRecaudaciones() {
        return workbook(recaudaciones(
            sheet(VENTAS,
                sale(DAY, unit, 2, "Producto", 30, null, 10.0),
                sale(DAY, null, 1, "Pan sin gluten", 23, null, null),
                sale(DAY, mamiCode, 1, "Torta de Mami", 50, null, null),
                sale(DAY, nut + "-MLI", 2, "Nuez 1/2 libra", 90, null, 29.48),
                sale(DAY, nut, 250, "Nuez", 52.5, null, 0.13),
                sale(DAY, unit, 1, "Producto", 10, "Mami precio costo", 10.0),
                sale(DAY, unit, 1, "Producto", 15, "Regalo Ali", 10.0),
                sale(DAY, unit, 1, "Producto", 10, "Mami rompió precio costo", 10.0),
                sale(AFTER, unit, 1, "Producto", 15, null, 10.0)),
            transacc(
                new Object[]{"Gasto", "julio", DAY, "Pan", -100, expenseCategory, accountA, null, null},
                // The spreadsheet says Venta; the system's sales category is used
                new Object[]{"Ingreso", "julio", DAY, "Ventas QR", 300, "Venta", accountA, null, null},
                new Object[]{"Transferencia", "julio", DAY, "Retiro", 200, "Retiro caja", accountA, accountB, "Depósito"}),
            productos("Gramo")));
    }

    private byte[] validPedidos() {
        return pedidos(
            new Object[]{1, "Pan mayo", supplier, 100, 10, "Caja", "Pagado", DAY, null, "En puerta", "Entregado", DAY, null},
            new Object[]{2, "Arcillas", supplier, 60, null, "QR", "A Cuenta", DAY, 20, "Terminal", "Entregado", DAY, null},
            new Object[]{3, "Sativa 1132", null, 45, null, null, "Por Pagar", null, null, "En puerta", "En camino", null, null},
            new Object[]{4, "Oasis", supplier, 30, null, null, "Consignacion", null, null, null, "Entregado", DAY, "Nota"});
    }

    // --- Tests ---

    @Test
    @DisplayName("Importa ventas, movimientos, caja, consumos y pedidos como históricos, sin tocar saldos ni la apertura")
    void importsEverySheetAsHistorical() {
        BigDecimal balanceBefore = jdbc.queryForObject(
            "SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, accountAId);
        var proposalBefore = cashSessionService.getOpeningProposal();

        MigrationResponse preview = migrationService.preview("Akhana Recaudaciones.xlsx", validRecaudaciones(),
            "Akhana Pedidos.xlsx", validPedidos(), CUTOFF);
        assertThat(preview.rejected()).isEmpty();
        assertThat(preview.imported()).isFalse();
        assertThat(preview.unidentifiedLines()).isEqualTo(1);
        assertThat(preview.unidentifiedTotal()).isEqualByComparingTo("23.00");
        assertThat(preview.thirdPartyDebts()).singleElement()
            .satisfies(debt -> {
                assertThat(debt.creditorName()).isEqualTo(mamiName);
                assertThat(debt.amount()).isEqualByComparingTo("50.00");
            });
        var ventas = preview.sheets().stream().filter(s -> s.sheet().equals("Ventas")).findFirst().orElseThrow();
        assertThat(ventas.read()).isEqualTo(9);
        assertThat(ventas.toImport()).isEqualTo(8);
        assertThat(ventas.afterCutoff()).isEqualTo(1);
        var july = preview.months().getFirst();
        // Normal: 30 + 23 + 90 + 52.50 + 15 of the gift without «precio costo»; profit of 30 - 20, 90 - 0.454 x 129.87,
        // 52.50 - 0.25 x 130 and 15 - 10
        assertThat(july.sales()).isEqualByComparingTo("210.50");
        assertThat(july.profit()).isEqualByComparingTo("66.04");
        // P-39: «Mami precio costo» and the breakage marked «precio costo» are at-cost sales, not consumptions
        assertThat(july.atCost()).isEqualByComparingTo("20.00");
        assertThat(july.unidentified()).isEqualByComparingTo("23.00");
        assertThat(july.thirdParty()).isEqualByComparingTo("50.00");
        assertThat(july.consumptions()).isEqualByComparingTo("10.00");
        assertThat(preview.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.sheet()).isEqualTo("Ventas");
            assertThat(notice.messages().getFirst()).contains("Regalo Ali", "precio costo");
        });

        MigrationResponse result = migrationService.importHistory("Akhana Recaudaciones.xlsx", validRecaudaciones(),
            "Akhana Pedidos.xlsx", validPedidos(), CUTOFF, "it-migration");
        entityManager.flush();
        assertThat(result.imported()).isTrue();
        UUID importId = jdbc.queryForObject("SELECT id FROM historical_imports WHERE imported_by = 'it-migration'", UUID.class);

        // Sales: one per day and type, own series, no cash session nor payments
        List<Map<String, Object>> sales = jdbc.queryForList(
            "SELECT id, sale_number, sale_type, total_amount, payment_method, cash_session_id FROM sales "
                + "WHERE historical_import_id = ? ORDER BY sale_type DESC", importId);
        assertThat(sales).hasSize(2);
        assertThat(sales).allSatisfy(s -> {
            assertThat((String) s.get("sale_number")).startsWith("HIS-");
            assertThat(s.get("payment_method")).isEqualTo("HISTORICO");
            assertThat(s.get("cash_session_id")).isNull();
        });
        assertThat(sales.get(0).get("sale_type")).isEqualTo("NORMAL");
        assertThat((BigDecimal) sales.get(0).get("total_amount")).isEqualByComparingTo("260.50");
        assertThat((BigDecimal) sales.get(1).get("total_amount")).isEqualByComparingTo("20.00");

        UUID normal = (UUID) sales.get(0).get("id");
        Map<String, Object> pound = jdbc.queryForMap(
            "SELECT quantity, weight_kg, presentation_name, unit_price, unit_cost FROM sale_items "
                + "WHERE sale_id = ? AND presentation_id IS NOT NULL", normal);
        assertThat(pound.get("presentation_name")).isEqualTo("1/2 libra");
        assertThat((BigDecimal) pound.get("weight_kg")).isEqualByComparingTo("0.454");
        assertThat((BigDecimal) pound.get("unit_price")).isEqualByComparingTo("45.00");
        assertThat((BigDecimal) pound.get("unit_cost")).isEqualByComparingTo("129.87");
        Map<String, Object> kilo = jdbc.queryForMap(
            "SELECT quantity, unit_price, unit_cost FROM sale_items WHERE sale_id = ? AND product_code = ? "
                + "AND presentation_id IS NULL", normal, nut);
        assertThat((BigDecimal) kilo.get("quantity")).isEqualByComparingTo("0.250");
        assertThat((BigDecimal) kilo.get("unit_price")).isEqualByComparingTo("210.00");
        assertThat((BigDecimal) kilo.get("unit_cost")).isEqualByComparingTo("130.00");
        Map<String, Object> free = jdbc.queryForMap(
            "SELECT free_name, unit_cost, assignment_status, assigned_by, assigned_at FROM sale_items "
                + "WHERE sale_id = ? AND free_line_type = 'UNIDENTIFIED'", normal);
        assertThat(free.get("free_name")).isEqualTo("Pan sin gluten");
        assertThat((BigDecimal) free.get("unit_cost")).isEqualByComparingTo("0");
        // POS-12 (28-10-2026): rows without a code are closed as unidentified for good by the import
        assertThat(free.get("assignment_status")).isEqualTo("SIN_IDENTIFICAR");
        assertThat(free.get("assigned_by")).isEqualTo("it-migration");
        assertThat(free.get("assigned_at")).isNotNull();
        BigDecimal debt = jdbc.queryForObject(
            "SELECT SUM(i.subtotal) FROM sale_items i JOIN creditors c ON c.id = i.creditor_id WHERE c.name = ?",
            BigDecimal.class, mamiName);
        assertThat(debt).isEqualByComparingTo("50.00");

        // Consumptions: only the Consumos row; gifts and breakages are sales (P-39)
        assertThat(jdbc.queryForList("SELECT comment FROM consumptions WHERE historical_import_id = ?",
            String.class, importId)).containsExactly((String) null);

        // Movements: historical, outside the balances and the opening balance lock (FIN-01, FIN-08)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE historical_import_id = ?",
            Integer.class, importId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT c.is_sales_default FROM financial_transactions t JOIN financial_categories c "
            + "ON c.id = t.category_id WHERE t.historical_import_id = ? AND t.type = 'INGRESO_MANUAL'", Boolean.class, importId))
            .isTrue();
        assertThat(jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class,
            accountAId)).isEqualByComparingTo(balanceBefore);
        assertThat(financialTransactionRepository.existsMovementsBesidesOpeningBalance(accountAId)).isFalse();

        // Cash session: closed with the amounts of the spreadsheet; the opening proposal ignores it (CAJ-10)
        Map<String, Object> session = jdbc.queryForMap(
            "SELECT status, difference, historical_expenses FROM cash_sessions WHERE historical_import_id = ?", importId);
        assertThat(session.get("status")).isEqualTo("CERRADA");
        assertThat((BigDecimal) session.get("difference")).isEqualByComparingTo("5.00");
        assertThat((BigDecimal) session.get("historical_expenses")).isEqualByComparingTo("50.00");
        assertThat(cashSessionService.getOpeningProposal()).isEqualTo(proposalBefore);

        // Purchases: payment status from the spreadsheet, payments without a movement (P-43)
        List<Map<String, Object>> purchases = jdbc.queryForList(
            "SELECT purchase_number, items_total, shipping_cost, paid_amount, consignment, delivery_status, "
                + "(SELECT COUNT(*) FROM purchase_items i WHERE i.purchase_id = p.id) AS lines "
                + "FROM purchases p WHERE historical_import_id = ? ORDER BY purchase_number", importId);
        assertThat(purchases).extracting(p -> p.get("purchase_number"))
            .containsExactly("PED-H00001", "PED-H00002", "PED-H00003", "PED-H00004");
        assertThat((BigDecimal) purchases.get(0).get("paid_amount")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) purchases.get(0).get("shipping_cost")).isEqualByComparingTo("10.00");
        assertThat((BigDecimal) purchases.get(1).get("paid_amount")).isEqualByComparingTo("20.00");
        assertThat(purchases.get(2).get("lines")).isEqualTo(1L);
        assertThat(purchases.get(2).get("delivery_status")).isEqualTo("PENDIENTE");
        assertThat(purchases.get(3).get("consignment")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_payments WHERE historical_import_id = ? "
            + "AND financial_transaction_id IS NULL", Integer.class, importId)).isEqualTo(2);

        // Read only, and only once
        assertThatThrownBy(() -> saleService.voidSale(normal, new VoidSaleRequest("Prueba"), "it-migration"))
            .hasMessageContaining("histórica");
        assertThatThrownBy(() -> migrationService.preview("Akhana Recaudaciones.xlsx", validRecaudaciones(),
            "Akhana Pedidos.xlsx", validPedidos(), CUTOFF))
            .hasMessage(MigrationService.ALREADY_DONE);
    }

    @Test
    @DisplayName("Rechaza filas con código, categoría, proveedor o unidad de venta faltantes, y no importa nada")
    void rejectsRowsAndImportsNothing() {
        byte[] recaudaciones = workbook(recaudaciones(
            sheet(VENTAS,
                sale(DAY, "IT" + tag + "-NO", 1, "No existe", 10, null, 5.0),
                sale(DAY, nut, 250, "Nuez", 52.5, null, 0.13)),
            transacc(new Object[]{"Gasto", "julio", DAY, "Envío", -15, "Envio " + tag, accountA, null, null}),
            productos(null)));
        byte[] pedidos = pedidos(
            new Object[]{1, "Sin proveedor", null, 100, null, "Caja", "Pagado", DAY, null, null, "Entregado", DAY, null},
            new Object[]{3, "Sativa", supplier, 45, null, null, "Por Pagar", null, null, null, null, DAY, null});

        MigrationResponse preview = migrationService.preview("R.xlsx", recaudaciones, "P.xlsx", pedidos, CUTOFF);

        assertThat(preview.rejected()).extracting(RowMessage::sheet, RowMessage::rowNumber).containsExactly(
            org.assertj.core.groups.Tuple.tuple("Pedidos", 2),
            org.assertj.core.groups.Tuple.tuple("Transacc.", 2),
            org.assertj.core.groups.Tuple.tuple("Ventas", 2),
            org.assertj.core.groups.Tuple.tuple("Ventas", 3));
        assertThat(preview.rejected()).flatExtracting(RowMessage::messages).containsExactly(
            "Falta el proveedor.",
            "La categoría Envio " + tag + " no existe en Finanzas.",
            "El código IT" + tag + "-NO no existe en el catálogo.",
            "El producto " + nut + " se vende por kilo: marcar «Se vende por» en Productos.");
        assertThatThrownBy(() -> migrationService.importHistory("R.xlsx", recaudaciones, "P.xlsx", pedidos, CUTOFF, "it"))
            .hasMessage(MigrationService.HAS_REJECTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM historical_imports", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sin una hoja o columna no se revisa nada")
    void missingSheetRejectsTheFiles() {
        Map<String, List<Object[]>> sheets = recaudaciones(sheet(new Object[]{"Fecha", "Codigo", "Cant", "Descripción",
            "Total"}), transacc(), productos("Gramo"));
        sheets.remove("Consumos");

        assertThatThrownBy(() -> migrationService.preview("R.xlsx", workbook(sheets), "P.xlsx", validPedidos(), CUTOFF))
            .hasMessage("Faltan las hojas o columnas: Consumos, Ventas › Costo unitario.");
    }
}
