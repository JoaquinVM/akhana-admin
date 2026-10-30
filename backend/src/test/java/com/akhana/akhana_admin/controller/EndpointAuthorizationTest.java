package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.JwtAuthenticationEntryPoint;
import com.akhana.akhana_admin.config.JwtAuthenticationFilter;
import com.akhana.akhana_admin.config.SecurityConfig;
import com.akhana.akhana_admin.exception.GlobalExceptionHandler;
import com.akhana.akhana_admin.dto.ReportFile;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.UserRepository;
import com.akhana.akhana_admin.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-13 / USR-05: every endpoint requires the permission of the screen that uses it (table 2.2): whoever opens a
 * screen can do everything in it. The administrator reaches every endpoint; the operator gets 403 on finances and users.
 * Request bodies are valid so that bean validation (which runs before @PreAuthorize) does not mask the result.
 */
@WebMvcTest(controllers = {
        CashSessionController.class,
        CategoryController.class,
        ConsumptionController.class,
        CreditorController.class,
        CustomerController.class,
        DashboardController.class,
        DebtController.class,
        FinancialAccountController.class,
        FinancialCategoryController.class,
        FinancialSummaryController.class,
        FinancialTransactionController.class,
        MigrationController.class,
        ProductController.class,
        ProductImportController.class,
        PurchaseController.class,
        QuickProductController.class,
        ReportController.class,
        SaleController.class,
        SupplierController.class,
        TagController.class,
        UserController.class,
        GlobalExceptionHandler.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class EndpointAuthorizationTest {

    private static final String ADMIN_TOKEN = "valid.administrator.jwt";
    private static final String OPERATOR_TOKEN = "valid.operator.jwt";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private static final String SALE = """
            {"items":[{"productId":"%s","quantity":1}],"paymentMethod":"EFECTIVO"}""".formatted(ID);
    private static final String VOID_SALE = """
            {"voidReason":"Error de cobro"}""";
    private static final String OPEN_CASH = """
            {"openingAmount":100.00}""";
    private static final String CLOSE_CASH = """
            {"closingAmount":100.00}""";
    private static final String NAME_COLOR = """
            {"name":"Snacks","color":"#ffffff"}""";
    private static final String PRODUCT = """
            {"code":"P-1","name":"Mani","categoryId":"%s","supplierId":"%s","buyPrice":1.00,"sellPrice":2.00}"""
            .formatted(ID, ID);
    private static final String SUPPLIER = """
            {"name":"Granel","code":"GR"}""";
    private static final String PURCHASE = """
            {"supplierId":"%s","items":[{"productId":"%s","quantity":1,"unitCost":5.00}]}""".formatted(ID, ID);
    private static final String PURCHASE_PAYMENT = """
            {"accountId":"%s","amount":50.00}""".formatted(ID);
    private static final String PURCHASE_DELIVERY = """
            {"deliveryType":"TERMINAL","deliveryStatus":"PENDIENTE"}""";
    private static final String CREDITOR = """
            {"name":"Mami"}""";
    private static final String REPAYMENT = """
            {"accountId":"%s","amount":50.00}""".formatted(ID);
    private static final String CUSTOMER = """
            {"name":"Tía Neida"}""";
    private static final String COLLECTION = """
            {"accountId":"%s","amount":50.00}""".formatted(ID);
    private static final String VOID_DEBT = """
            {"voidReason":"Error de carga"}""";
    private static final String VOID_PURCHASE = """
            {"voidReason":"Error de carga"}""";
    private static final String CONSUMPTION = """
            {"items":[{"productId":"%s","quantity":1}]}""".formatted(ID);
    private static final String VOID_CONSUMPTION = """
            {"voidReason":"Error de carga"}""";
    private static final String QUICK_GROUP = """
            {"name":"Bebidas"}""";
    private static final String REORDER_GROUPS = """
            {"groupIds":["%s"]}""".formatted(ID);
    private static final String REORDER_ITEMS = """
            {"productIds":["%s"]}""".formatted(ID);
    private static final String CASH_EXPENSE = """
            {"amount":5.00,"categoryId":"%s","description":"Almuerzo"}""".formatted(ID);
    private static final String CASH_TRANSFER = """
            {"direction":"RETIRO","accountId":"%s","amount":5.00,"comment":"Depósito"}""".formatted(ID);
    private static final String TRANSACTION = """
            {"type":"GASTO_MANUAL","sourceAccountId":"%s","amount":5.00,"description":"Luz"}""".formatted(ID);
    private static final String VOID_TRANSACTION = """
            {"voidReason":"Registrado dos veces"}""";
    private static final String ACCOUNT = """
            {"name":"Bisa"}""";
    private static final String FINANCIAL_CATEGORY = """
            {"name":"Comida","type":"GASTO","color":"#ffffff"}""";
    private static final String CREATE_USER = """
            {"fullName":"Ana Rojas","username":"arojas","password":"password123","role":"OPERATOR"}""";
    private static final String UPDATE_USER = """
            {"fullName":"Ana Rojas","role":"OPERATOR"}""";
    // Sent as a multipart "file" part instead of a JSON body (CAT-06)
    private static final String XLSX_FILE = "<xlsx>";
    // The two spreadsheets and the cutoff date of the historical migration (FIN-11)
    private static final String MIGRATION_FILES = "<migration>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean private CashSessionService cashSessionService;
    @MockitoBean private CategoryService categoryService;
    @MockitoBean private ConsumptionService consumptionService;
    @MockitoBean private CreditorService creditorService;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private FinancialAccountService financialAccountService;
    @MockitoBean private FinancialCategoryService financialCategoryService;
    @MockitoBean private FinancialSummaryService financialSummaryService;
    @MockitoBean private FinancialTransactionService financialTransactionService;
    @MockitoBean private MigrationService migrationService;
    @MockitoBean private ProductService productService;
    @MockitoBean private ProductImportService productImportService;
    @MockitoBean private PurchaseService purchaseService;
    @MockitoBean private QuickProductService quickProductService;
    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private ReportService reportService;
    @MockitoBean private ReportExportService reportExportService;
    @MockitoBean private SaleService saleService;
    @MockitoBean private SupplierService supplierService;
    @MockitoBean private TagService tagService;
    @MockitoBean private UserService userService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private FinancialAccountRepository financialAccountRepository;

    record Endpoint(HttpMethod method, String url, String body, boolean operatorAllowed) {
        @Override
        public String toString() {
            return method + " " + url + (operatorAllowed ? " (operador sí)" : " (operador no)");
        }
    }

    private static Endpoint both(HttpMethod method, String url, String body) {
        return new Endpoint(method, url, body, true);
    }

    private static Endpoint adminOnly(HttpMethod method, String url, String body) {
        return new Endpoint(method, url, body, false);
    }

    static Stream<Endpoint> endpoints() {
        HttpMethod get = HttpMethod.GET, post = HttpMethod.POST, put = HttpMethod.PUT,
                patch = HttpMethod.PATCH, delete = HttpMethod.DELETE;
        return Stream.of(
                // Caja: abrir, ver y cerrar; historial
                both(post, "/api/cash-sessions/open", OPEN_CASH),
                both(get, "/api/cash-sessions/opening-proposal", null),
                both(get, "/api/cash-sessions/current", null),
                both(post, "/api/cash-sessions/current/close", CLOSE_CASH),
                both(get, "/api/cash-sessions", null),
                both(get, "/api/cash-sessions/" + ID, null),
                // Ventas: registrar, anular (POS) e historial
                both(post, "/api/sales", SALE),
                both(get, "/api/sales", null),
                both(get, "/api/sales/sellers", null),
                both(get, "/api/sales/" + ID, null),
                both(get, "/api/sales/session/" + ID, null),
                both(post, "/api/sales/" + ID + "/void", VOID_SALE),
                both(post, "/api/sales/" + ID + "/items/" + ID + "/assign", "{\"productId\":\"" + ID + "\"}"),
                both(post, "/api/sales/" + ID + "/items/" + ID + "/close-unidentified", null),
                // Catálogo: productos (costo y precio), categorías, etiquetas, productos rápidos
                both(get, "/api/products", null),
                both(get, "/api/products/" + ID, null),
                both(post, "/api/products", PRODUCT),
                both(put, "/api/products/" + ID, PRODUCT),
                both(patch, "/api/products/" + ID + "/status?status=INACTIVO", null),
                both(delete, "/api/products/" + ID, null),
                // Importación de productos con el permiso de la pantalla (CAT-06, P-33)
                both(post, "/api/products/import/preview", XLSX_FILE),
                both(post, "/api/products/import", XLSX_FILE),
                both(get, "/api/categories", null),
                both(get, "/api/categories/" + ID, null),
                both(post, "/api/categories", NAME_COLOR),
                both(put, "/api/categories/" + ID, NAME_COLOR),
                both(delete, "/api/categories/" + ID, null),
                both(get, "/api/tags", null),
                both(get, "/api/tags/" + ID, null),
                both(post, "/api/tags", NAME_COLOR),
                both(put, "/api/tags/" + ID, NAME_COLOR),
                both(delete, "/api/tags/" + ID, null),
                both(get, "/api/quick-products/groups", null),
                both(get, "/api/quick-products/groups/" + ID, null),
                both(post, "/api/quick-products/groups", QUICK_GROUP),
                both(put, "/api/quick-products/groups/" + ID, QUICK_GROUP),
                both(delete, "/api/quick-products/groups/" + ID, null),
                both(put, "/api/quick-products/groups/reorder", REORDER_GROUPS),
                both(put, "/api/quick-products/groups/" + ID + "/items/reorder", REORDER_ITEMS),
                // Proveedores
                both(get, "/api/suppliers", null),
                both(get, "/api/suppliers/" + ID, null),
                both(post, "/api/suppliers", SUPPLIER),
                both(put, "/api/suppliers/" + ID, SUPPLIER),
                both(delete, "/api/suppliers/" + ID, null),
                // Compras: pedidos, pagos, cuentas para pagar, precio sugerido, entrega, por pagar y anulación (COM-02 a COM-09)
                both(get, "/api/purchases", null),
                both(get, "/api/purchases/" + ID, null),
                both(post, "/api/purchases", PURCHASE),
                both(post, "/api/purchases/" + ID + "/payments", PURCHASE_PAYMENT),
                both(get, "/api/purchases/payment-accounts", null),
                both(get, "/api/purchases/price-suggestion?productId=" + ID + "&newCost=12.30", null),
                both(get, "/api/purchases/payables", null),
                both(put, "/api/purchases/" + ID + "/delivery", PURCHASE_DELIVERY),
                both(post, "/api/purchases/" + ID + "/void", VOID_PURCHASE),
                both(post, "/api/purchases/" + ID + "/payments/" + ID + "/void", VOID_PURCHASE),
                // Deudas con personas
                both(get, "/api/creditors", null),
                both(get, "/api/creditors/" + ID, null),
                both(post, "/api/creditors", CREDITOR),
                both(put, "/api/creditors/" + ID, CREDITOR),
                both(delete, "/api/creditors/" + ID, null),
                both(get, "/api/creditors/" + ID + "/history", null),
                both(post, "/api/creditors/" + ID + "/repayments", REPAYMENT),
                both(post, "/api/creditors/" + ID + "/repayments/" + ID + "/void", VOID_DEBT),
                both(get, "/api/debts/accounts", null),
                // Cuentas por cobrar: clientes, historial, cobros y su anulación (DEU-05 a DEU-07)
                both(get, "/api/customers", null),
                both(get, "/api/customers/" + ID, null),
                both(post, "/api/customers", CUSTOMER),
                both(put, "/api/customers/" + ID, CUSTOMER),
                both(delete, "/api/customers/" + ID, null),
                both(get, "/api/customers/" + ID + "/history", null),
                both(post, "/api/customers/" + ID + "/collections", COLLECTION),
                both(post, "/api/customers/" + ID + "/collections/" + ID + "/void", VOID_DEBT),
                // Consumos internos: listar, detalle, registrar y anular (CON-01)
                both(get, "/api/consumptions", null),
                both(get, "/api/consumptions/" + ID, null),
                both(post, "/api/consumptions", CONSUMPTION),
                both(post, "/api/consumptions/" + ID + "/void", VOID_CONSUMPTION),
                // Gastos de caja desde el POS: registrar y anular
                both(post, "/api/finances/transactions/cash-expense", CASH_EXPENSE),
                both(post, "/api/finances/transactions/cash-expense/" + ID + "/void", VOID_TRANSACTION),
                // Ingresos y retiros de caja desde el POS (CAJ-08)
                both(get, "/api/finances/transactions/cash-transfer/accounts", null),
                both(post, "/api/finances/transactions/cash-transfer", CASH_TRANSFER),
                both(post, "/api/finances/transactions/cash-transfer/" + ID + "/void", VOID_TRANSACTION),
                both(get, "/api/finances/transactions/session/" + ID, null),
                both(get, "/api/finances/categories", null),
                adminOnly(post, "/api/finances/transactions/" + ID + "/void", VOID_TRANSACTION),
                // Finanzas
                adminOnly(get, "/api/finances/transactions", null),
                adminOnly(get, "/api/finances/transactions/" + ID, null),
                adminOnly(post, "/api/finances/transactions", TRANSACTION),
                adminOnly(put, "/api/finances/transactions/" + ID, TRANSACTION),
                adminOnly(get, "/api/finances/accounts", null),
                adminOnly(get, "/api/finances/accounts/" + ID, null),
                adminOnly(get, "/api/finances/accounts/" + ID + "/transactions", null),
                adminOnly(post, "/api/finances/accounts", ACCOUNT),
                adminOnly(put, "/api/finances/accounts/" + ID, ACCOUNT),
                adminOnly(put, "/api/finances/accounts/" + ID + "/qr", null),
                adminOnly(put, "/api/finances/accounts/" + ID + "/opening-balance", "{\"amount\":10}"),
                adminOnly(delete, "/api/finances/accounts/" + ID, null),
                adminOnly(get, "/api/finances/categories/" + ID, null),
                adminOnly(post, "/api/finances/categories", FINANCIAL_CATEGORY),
                adminOnly(put, "/api/finances/categories/" + ID, FINANCIAL_CATEGORY),
                adminOnly(put, "/api/finances/categories/" + ID + "/sales-default", null),
                adminOnly(delete, "/api/finances/categories/" + ID, null),
                adminOnly(get, "/api/finances/summary", null),
                // Dashboard (DSH-01 to DSH-04)
                adminOnly(get, "/api/dashboard", null),
                // Reportes
                adminOnly(get, "/api/reports/sales-by-day", null),
                adminOnly(get, "/api/reports/sales-by-supplier", null),
                adminOnly(get, "/api/reports/transactions-by-category", null),
                adminOnly(get, "/api/reports/totals", null),
                adminOnly(get, "/api/reports/consumptions", null),
                adminOnly(get, "/api/reports/sales-by-day/export", null),
                adminOnly(get, "/api/reports/sales-by-supplier/export", null),
                adminOnly(get, "/api/reports/transactions-by-category/export", null),
                adminOnly(get, "/api/reports/totals/export", null),
                adminOnly(get, "/api/reports/consumptions/export", null),
                adminOnly(post, "/api/migration/preview", MIGRATION_FILES),
                adminOnly(post, "/api/migration", MIGRATION_FILES),
                adminOnly(get, "/api/migration/status", null),
                // Usuarios
                adminOnly(get, "/api/users", null),
                adminOnly(get, "/api/users/" + ID, null),
                adminOnly(get, "/api/users/roles", null),
                adminOnly(post, "/api/users", CREATE_USER),
                adminOnly(put, "/api/users/" + ID, UPDATE_USER),
                adminOnly(delete, "/api/users/" + ID, null)
        );
    }

    @BeforeEach
    void setUp() {
        stubUser(ADMIN_TOKEN, "admin", Role.ADMINISTRATOR);
        stubUser(OPERATOR_TOKEN, "operator", Role.OPERATOR);
        when(userService.getUsers(any(), any())).thenReturn(Page.empty());
        when(financialAccountRepository.findByIsCashAccountTrue())
                .thenReturn(Optional.of(FinancialAccount.builder().id(UUID.randomUUID()).build()));
        ReportFile file = new ReportFile("akhana-report.xlsx", new byte[0]);
        when(reportExportService.exportSalesByDay(any(), any())).thenReturn(file);
        when(reportExportService.exportSalesBySupplier(any(), any())).thenReturn(file);
        when(reportExportService.exportTransactionsByCategory(any(), any())).thenReturn(file);
        when(reportExportService.exportTotals(any(), any())).thenReturn(file);
        when(reportExportService.exportConsumptions(any(), any())).thenReturn(file);
    }

    private void stubUser(String token, String username, Role role) {
        User user = User.builder().id(UUID.randomUUID()).username(username).role(role).active(true).build();
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.isTokenValid(token, username)).thenReturn(true);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
    }

    private RequestBuilder call(Endpoint endpoint, String token) {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.url())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (XLSX_FILE.equals(endpoint.body())) {
            return multipart(endpoint.url())
                    .file(new MockMultipartFile("file", "Productos.xlsx", null, new byte[]{1}))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (MIGRATION_FILES.equals(endpoint.body())) {
            return multipart(endpoint.url())
                    .file(new MockMultipartFile("recaudaciones", "Akhana Recaudaciones.xlsx", null, new byte[]{1}))
                    .file(new MockMultipartFile("pedidos", "Akhana Pedidos.xlsx", null, new byte[]{1}))
                    .param("cutoffDate", "2026-10-26")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return builder;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("El administrador accede a todos los endpoints")
    void administrator_isAllowedEverywhere(Endpoint endpoint) throws Exception {
        int status = mockMvc.perform(call(endpoint, ADMIN_TOKEN)).andReturn().getResponse().getStatus();
        assertThat(status).as(endpoint.toString()).isBetween(200, 299);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("El operador hace todo en sus pantallas y recibe 403 en finanzas y usuarios")
    void operator_isAllowedOnlyForDayToDayActions(Endpoint endpoint) throws Exception {
        var result = mockMvc.perform(call(endpoint, OPERATOR_TOKEN));
        if (endpoint.operatorAllowed()) {
            assertThat(result.andReturn().getResponse().getStatus()).as(endpoint.toString()).isBetween(200, 299);
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("No tiene permiso para realizar esta acción."));
        }
    }

    @Test
    @DisplayName("Sin token, un endpoint protegido responde 401")
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cada endpoint tiene @PreAuthorize y está en la matriz")
    void everyEndpoint_isProtectedAndCovered() {
        var handlers = handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().equals(UserController.class.getPackageName()))
                .toList();

        assertThat(handlers).allSatisfy(handler -> assertThat(
                handler.hasMethodAnnotation(PreAuthorize.class)
                        || handler.getBeanType().isAnnotationPresent(PreAuthorize.class))
                .as(handler.toString()).isTrue());
        assertThat(endpoints().map(Endpoint::toString).distinct().count()).isEqualTo(handlers.size());
    }
}
