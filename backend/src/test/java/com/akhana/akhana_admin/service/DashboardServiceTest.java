package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.CashSessionSummaryResponse;
import com.akhana.akhana_admin.dto.CreditorResponse;
import com.akhana.akhana_admin.dto.CustomerResponse;
import com.akhana.akhana_admin.dto.DashboardResponse;
import com.akhana.akhana_admin.dto.DashboardResponse.RankRow;
import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.dto.PayablesResponse;
import com.akhana.akhana_admin.model.CashSessionStatus;
import com.akhana.akhana_admin.repository.ConsumptionRepository;
import com.akhana.akhana_admin.repository.DashboardRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.DashboardServiceImpl;
import com.akhana.akhana_admin.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Dashboard (DSH-01 to DSH-04) on top of the real reports (FIN-10, CON-03); scenarios of specs/dashboard.md
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final ZoneId STORE = ZoneId.of("America/La_Paz");
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_30 = LocalDate.of(2026, 10, 30);
    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final UUID NUEZ = UUID.randomUUID();
    private static final UUID MANI = UUID.randomUUID();

    @Mock private SaleRepository saleRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;
    @Mock private ConsumptionRepository consumptionRepository;
    @Mock private DashboardRepository dashboardRepository;
    @Mock private CashSessionService cashSessionService;
    @Mock private FinancialSummaryService financialSummaryService;
    @Mock private PurchaseService purchaseService;
    @Mock private CreditorService creditorService;
    @Mock private CustomerService customerService;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        ReportServiceImpl reportService = new ReportServiceImpl(saleRepository, financialTransactionRepository,
                consumptionRepository);
        dashboardService = new DashboardServiceImpl(reportService, dashboardRepository, consumptionRepository,
                cashSessionService, financialSummaryService, purchaseService, creditorService, customerService);
        when(dashboardRepository.sumPayments(any(), any())).thenReturn(List.<Object[]>of(
                new Object[] {new BigDecimal("1200.00"), new BigDecimal("800.50"), new BigDecimal("104.00"),
                        new BigDecimal("2745"), 1L}));
        when(cashSessionService.findCurrentSession()).thenReturn(Optional.empty());
        when(financialSummaryService.getSummary()).thenReturn(FinancialSummaryResponse.builder()
                .totalBalance(new BigDecimal("5000"))
                .accounts(List.of(FinancialAccountResponse.builder().id(UUID.randomUUID()).name("Caja")
                        .color("#16a34a").currentBalance(new BigDecimal("1500")).build()))
                .build());
        when(purchaseService.getPayables(null)).thenReturn(new PayablesResponse(new BigDecimal("320.00"), List.of()));
        when(creditorService.getCreditors(null, null)).thenReturn(List.of());
        when(customerService.getCustomers(null, null)).thenReturn(List.of());
    }

    @Test
    @DisplayName("DSH-01: ventas, utilidad, margen, tickets y ticket promedio de las ventas normales; al costo y consumos aparte")
    void indicatorsOfNormalSales() {
        stubDays(OCT_1, row("2026-10-17", "NORMAL", 30, "2745.00", "2104.16"),
                row("2026-10-17", "AL_COSTO", 1, "104.00", "104.00"));
        stubConsumptions(OCT_1, "35.50");

        DashboardResponse.Indicators current = dashboardService.getDashboard(OCT_1, OCT_30).current();

        assertEquals(new BigDecimal("2745.00"), current.sales());
        assertEquals(new BigDecimal("2104.16"), current.cost());
        assertEquals(new BigDecimal("640.84"), current.profit());
        assertEquals(new BigDecimal("23.35"), current.margin());
        assertEquals(30, current.tickets());
        assertEquals(new BigDecimal("91.50"), current.averageTicket());
        assertEquals(1, current.atCostSales());
        assertEquals(new BigDecimal("104.00"), current.atCostTotal());
        assertEquals(new BigDecimal("35.50"), current.consumptionTotal());
    }

    @Test
    @DisplayName("DSH-01: el mes en curso hasta hoy se compara con los mismos días del mes anterior")
    void comparesWithTheSameDaysOfLastMonth() {
        stubDays(OCT_1, row("2026-10-02", "NORMAL", 10, "1100.00", "800.00"));
        stubDays(SEP_1, row("2026-09-02", "NORMAL", 8, "1000.00", "750.00"));

        DashboardResponse dashboard = dashboardService.getDashboard(OCT_1, OCT_30);

        assertEquals(SEP_1, dashboard.previousFrom());
        assertEquals(LocalDate.of(2026, 9, 30), dashboard.previousTo());
        assertEquals(new BigDecimal("1000.00"), dashboard.previous().sales());
        assertEquals(new BigDecimal("250.00"), dashboard.previous().profit());
        assertEquals(8, dashboard.previous().tickets());
        assertEquals(new BigDecimal("125.00"), dashboard.previous().averageTicket());
    }

    @Test
    @DisplayName("DSH-01: un día se compara con el anterior, una semana con la anterior y marzo con febrero")
    void previousRanges() {
        assertArrayEquals(new LocalDate[] {LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 29)},
                DashboardServiceImpl.previousRange(OCT_30, OCT_30));
        assertArrayEquals(new LocalDate[] {LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 23)},
                DashboardServiceImpl.previousRange(LocalDate.of(2026, 10, 26), OCT_30));
        assertArrayEquals(new LocalDate[] {LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)},
                DashboardServiceImpl.previousRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        // The 1st alone is a day: it compares with the day before, not with the 1st of last month
        assertArrayEquals(new LocalDate[] {LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30)},
                DashboardServiceImpl.previousRange(OCT_1, OCT_1));
    }

    @Test
    @DisplayName("Sin ventas: margen y ticket promedio vacíos, y cada día del período en cero")
    void emptyPeriod() {
        DashboardResponse dashboard = dashboardService.getDashboard(OCT_1, LocalDate.of(2026, 10, 3));

        assertNull(dashboard.current().margin());
        assertNull(dashboard.current().averageTicket());
        assertEquals(0, dashboard.current().tickets());
        assertEquals(3, dashboard.days().size());
        assertEquals(new BigDecimal("0.00"), dashboard.days().get(1).sales());
        assertEquals(3, dashboard.previousDays().size());
    }

    @Test
    @DisplayName("DSH-02: cada día del período con sus ventas normales y utilidad; el cobro por efectivo, QR y crédito")
    void daysAndPayments() {
        stubDays(OCT_1, row("2026-10-02", "NORMAL", 2, "100.00", "60.00"));

        DashboardResponse dashboard = dashboardService.getDashboard(OCT_1, LocalDate.of(2026, 10, 3));

        assertEquals(List.of(OCT_1, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)),
                dashboard.days().stream().map(DashboardResponse.DayPoint::date).toList());
        assertEquals(new BigDecimal("100.00"), dashboard.days().get(1).sales());
        assertEquals(new BigDecimal("40.00"), dashboard.days().get(1).profit());
        assertEquals(new BigDecimal("1200.00"), dashboard.payments().cash());
        assertEquals(new BigDecimal("800.50"), dashboard.payments().qr());
        assertEquals(new BigDecimal("104.00"), dashboard.payments().credit());
        assertEquals(new BigDecimal("2745.00"), dashboard.payments().historicalTotal());
        assertEquals(1, dashboard.payments().historicalSales());
    }

    @Test
    @DisplayName("DSH-03: el descuento general se reparte entre los productos y las líneas por asignar no cuentan")
    void topProductsWithGeneralDiscount() {
        // Sale of 35: 2 x 10 of Maní (cost 6) and 0.250 kg x 80 of Nuez (cost 60/kg), general discount 5,
        // plus a pending line of 15 that keeps its amount (P-44)
        when(dashboardRepository.findProductLines(any(), any())).thenReturn(List.of(
                productLine("S1", "50.00", MANI, "Maní", "2", "20.00", "12.00", true, false),
                productLine("S1", "50.00", NUEZ, "Nuez", "0.250", "20.00", "15.00", true, false),
                productLine("S1", "50.00", NUEZ, "Libre", "1", "15.00", "0", false, true),
                productLine("S2", "80.00", NUEZ, "Nuez", "1.000", "80.00", "60.00", true, false)));

        DashboardResponse dashboard = dashboardService.getDashboard(OCT_1, OCT_30);

        RankRow nuez = dashboard.topProductsBySales().getFirst();
        assertEquals("Nuez", nuez.name());
        assertEquals(new BigDecimal("1.250"), nuez.quantity());
        assertEquals(new BigDecimal("97.50"), nuez.sales());
        assertEquals(new BigDecimal("22.50"), nuez.profit());
        RankRow mani = dashboard.topProductsBySales().get(1);
        assertEquals(new BigDecimal("17.50"), mani.sales());
        assertEquals(new BigDecimal("5.50"), mani.profit());
        assertEquals(2, dashboard.topProductsBySales().size());
    }

    @Test
    @DisplayName("DSH-03: los proveedores vienen del reporte por proveedor y los gastos por categoría en positivo, de mayor a menor")
    void suppliersAndExpenses() {
        UUID comida = UUID.randomUUID();
        UUID pedido = UUID.randomUUID();
        UUID bohemia = UUID.randomUUID();
        UUID alma = UUID.randomUUID();
        when(saleRepository.findReportLines(any(), any())).thenReturn(List.of(
                new Object[] {"S1", "2026-10", "NORMAL", new BigDecimal("100.00"), bohemia, "Bohemia",
                        BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("90.00"), true, false},
                new Object[] {"S2", "2026-10", "NORMAL", new BigDecimal("60.00"), alma, "Alma",
                        BigDecimal.ONE, new BigDecimal("60.00"), new BigDecimal("20.00"), true, false}));
        when(financialTransactionRepository.sumByMonthAndCategory(any(), any())).thenReturn(List.of(
                new Object[] {"2026-10", "GASTO", comida, "Comida", new BigDecimal("250.00")},
                new Object[] {"2026-10", "GASTO", pedido, "Pedido", new BigDecimal("4100.00")},
                new Object[] {"2026-10", "INGRESO", UUID.randomUUID(), "Venta", new BigDecimal("9000.00")}));

        DashboardResponse dashboard = dashboardService.getDashboard(OCT_1, OCT_30);

        assertEquals(List.of("Bohemia", "Alma"), dashboard.topSuppliersBySales().stream().map(RankRow::name).toList());
        assertEquals(List.of("Alma", "Bohemia"), dashboard.topSuppliersByProfit().stream().map(RankRow::name).toList());
        assertEquals(List.of("Pedido", "Comida"),
                dashboard.expenses().stream().map(DashboardResponse.CategoryAmount::categoryName).toList());
        assertEquals(new BigDecimal("4100.00"), dashboard.expenses().getFirst().amount());
        assertEquals(new BigDecimal("4350.00"), dashboard.expenseTotal());
    }

    @Test
    @DisplayName("DSH-04: caja abierta, saldos por cuenta y deudas por pagar y por cobrar")
    void now() {
        when(cashSessionService.findCurrentSession()).thenReturn(Optional.of(new CashSessionSummaryResponse(
                UUID.randomUUID(), 412L, CashSessionStatus.ABIERTA, new BigDecimal("300.00"), null, "ana",
                Instant.parse("2026-10-30T12:30:00Z"), null, null, null, null, new BigDecimal("900.00"),
                new BigDecimal("400.00"), new BigDecimal("1300.00"), new BigDecimal("1200.00"), null, 17, false)));
        when(creditorService.getCreditors(null, null)).thenReturn(List.of(creditor("150.00"), creditor("50.25")));
        when(customerService.getCustomers(null, null)).thenReturn(List.of(customer("104.00")));

        DashboardResponse.Now now = dashboardService.getDashboard(OCT_1, OCT_30).now();

        assertTrue(now.cash().open());
        assertEquals(412L, now.cash().sessionNumber());
        assertEquals(new BigDecimal("1200.00"), now.cash().expectedCash());
        assertEquals(17, now.cash().salesCount());
        assertEquals("Caja", now.accounts().getFirst().name());
        assertEquals(new BigDecimal("1500.00"), now.accounts().getFirst().balance());
        assertEquals(new BigDecimal("5000.00"), now.totalBalance());
        assertEquals(new BigDecimal("320.00"), now.payables());
        assertEquals(new BigDecimal("200.25"), now.creditorsDebt());
        assertEquals(new BigDecimal("104.00"), now.receivables());
    }

    @Test
    @DisplayName("Con la caja cerrada lo indica sin error")
    void closedCash() {
        DashboardResponse.CashNow cash = dashboardService.getDashboard(OCT_1, OCT_30).now().cash();

        assertFalse(cash.open());
        assertNull(cash.sessionNumber());
    }

    @Test
    @DisplayName("Un rango invertido da el mismo error que los reportes")
    void invertedRange() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dashboardService.getDashboard(OCT_30, OCT_1));
        assertEquals("La fecha desde no puede ser posterior a la fecha hasta.", error.getMessage());
    }

    private void stubDays(LocalDate from, Object[]... rows) {
        when(saleRepository.sumByDayAndType(eq(from.atStartOfDay(STORE).toInstant()), any()))
                .thenReturn(List.of(rows));
    }

    private void stubConsumptions(LocalDate from, String total) {
        when(consumptionRepository.sumByMonth(eq(from), any())).thenReturn(List.<Object[]>of(
                new Object[] {from.toString().substring(0, 7), 1L, new BigDecimal(total)}));
    }

    private static Object[] row(String day, String type, long sales, String total, String cost) {
        return new Object[] {day, type, sales, BigDecimal.ONE, new BigDecimal(total), new BigDecimal(cost),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static Object[] productLine(String sale, String saleTotal, UUID product, String name, String quantity,
                                        String subtotal, String cost, boolean normal, boolean free) {
        return new Object[] {sale, new BigDecimal(saleTotal), product, name, new BigDecimal(quantity),
                new BigDecimal(subtotal), new BigDecimal(cost), normal, free};
    }

    private static CreditorResponse creditor(String debt) {
        return new CreditorResponse(UUID.randomUUID(), "Mami", null, null, null, new BigDecimal(debt),
                null, null, null, null, null, null);
    }

    private static CustomerResponse customer(String balance) {
        return new CustomerResponse(UUID.randomUUID(), "Tienda amiga", null, null, null, new BigDecimal(balance),
                null, null, null, null, null, null);
    }
}
