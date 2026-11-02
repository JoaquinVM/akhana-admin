package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.DashboardService;
import com.akhana.akhana_admin.service.FinancialTransactionService;
import com.akhana.akhana_admin.service.ReportService;
import com.akhana.akhana_admin.service.SaleService;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CAJ-14 against PostgreSQL: the whole cycle with the real services. Everything runs in the transaction of the test,
 * rolled back at the end; the persistence context is cleared between steps, as between requests.
 */
@SpringBootTest
@Transactional
class CashReopenIntegrationTest {

    private static final String USER = "it-reopen";

    @Autowired private CashSessionService cashSessionService;
    @Autowired private SaleService saleService;
    @Autowired private FinancialTransactionService financialTransactionService;
    @Autowired private ReportService reportService;
    @Autowired private DashboardService dashboardService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private LocalDate day;
    private UUID productId;
    private UUID expenseCategoryId;

    @BeforeEach
    void setUp() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assumeTrue(open != null && open == 0, "La base ya tiene una caja abierta");
        CashTestDays.movePastSessionsAway(jdbc);
        day = StoreFormat.today().minusDays(2);
        CashTestDays.seedClosedSession(jdbc, day.minusDays(1), "100.00", USER);
        productId = createProduct(new BigDecimal("10.00"));
        expenseCategoryId = jdbc.queryForObject("INSERT INTO financial_categories (name, type, color, created_by) "
            + "VALUES (?, 'GASTO', '#000000', ?) RETURNING id", UUID.class, "IT gasto " + UUID.randomUUID(), USER);
    }

    // --- steps ---

    private CashSessionSummaryResponse open(String amount, String comment) {
        CashSessionSummaryResponse opened = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal(amount), comment, day, null, null), USER);
        next();
        return opened;
    }

    private SaleResponse sellInCash(int units) {
        BigDecimal total = new BigDecimal("10.00").multiply(BigDecimal.valueOf(units));
        SaleResponse sale = saleService.registerSale(new SaleDetailRequest(
            List.of(new SaleItemRequest(productId, BigDecimal.valueOf(units), null)), null, PaymentMethod.EFECTIVO, null, null, total, null), USER);
        next();
        return sale;
    }

    private FinancialTransactionResponse spend(String amount) {
        UUID cash = jdbc.queryForObject("SELECT id FROM financial_accounts WHERE is_cash_account = TRUE", UUID.class);
        FinancialTransactionResponse expense = financialTransactionService.createTransaction(FinancialTransactionRequest.builder()
            .type(FinancialTransactionType.GASTO_MANUAL).sourceAccountId(cash).categoryId(expenseCategoryId)
            .amount(new BigDecimal(amount)).description("Gasto IT").build(), USER);
        next();
        return expense;
    }

    private CashSessionSummaryResponse close(String amount, CashCutDto... cuts) {
        CashSessionSummaryResponse closed = cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal(amount), "Cierre " + amount, List.of(cuts)), "ana");
        next();
        return closed;
    }

    private CashSessionSummaryResponse reopen(UUID id, String reason) {
        CashSessionSummaryResponse reopened = cashSessionService.reopenSession(id, new ReopenCashRequest(reason), "luis");
        next();
        return reopened;
    }

    private void next() {
        entityManager.flush();
        entityManager.clear();
    }

    private static CashCutDto cut(String denomination, int quantity) {
        return new CashCutDto(new BigDecimal(denomination), quantity, 0, null);
    }

    private BigDecimal cashBalance() {
        return jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE is_cash_account = TRUE", BigDecimal.class);
    }

    private List<String> adjustments(UUID session) {
        return jdbc.queryForList("SELECT description || '|' || amount || '|' || status FROM financial_transactions t "
            + "JOIN financial_categories c ON c.id = t.category_id WHERE t.cash_session_id = ? "
            + "AND c.system_key IN ('CASH_SURPLUS', 'CASH_SHORTAGE') ORDER BY t.created_at, t.description", String.class, session);
    }

    private UUID adjustmentId(UUID session, String status) {
        return jdbc.queryForObject("SELECT t.id FROM financial_transactions t JOIN financial_categories c ON c.id = t.category_id "
            + "WHERE t.cash_session_id = ? AND c.system_key IN ('CASH_SURPLUS', 'CASH_SHORTAGE') AND t.status = ? "
            + "AND t.description LIKE '%al cierre%' LIMIT 1", UUID.class, session, status);
    }

    private UUID createProduct(BigDecimal price) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT categoria " + suffix, USER);
        UUID supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT proveedor " + suffix, "ITP-" + suffix, USER);
        return jdbc.queryForObject("INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, "
            + "percentage_profit, created_by) VALUES (?, ?, ?, ?, 0.00, ?, ?, 0.00, ?) RETURNING id",
            UUID.class, "IT-" + suffix, "IT producto " + suffix, category, supplier, price, price, USER);
    }

    // --- scenarios ---

    @Test
    @DisplayName("Ciclo completo: cerrar con faltante, reabrir, vender, gastar y recerrar: esperado, ajuste, saldo = contado, cortes e historial")
    void fullCycle() {
        CashSessionSummaryResponse opened = open("100.00", "Carga atrasada");
        UUID id = opened.id();
        sellInCash(1);
        assertThat(cashBalance()).isEqualByComparingTo("110.00");

        // Closes short by 5: Caja follows the count
        CashSessionSummaryResponse closed = close("105.00", cut("100", 1), cut("5", 1));
        assertThat(closed.difference()).isEqualByComparingTo("-5.00");
        assertThat(cashBalance()).isEqualByComparingTo("105.00");

        // Reopens: Caja goes back, the adjustment stays voided, the session keeps its data
        CashSessionSummaryResponse reopened = reopen(id, "Faltó cargar ventas");
        assertThat(cashBalance()).isEqualByComparingTo("110.00");
        assertThat(reopened.status().name()).isEqualTo("ABIERTA");
        assertThat(reopened.sessionNumber()).isEqualTo(opened.sessionNumber());
        assertThat(reopened.operationDate()).isEqualTo(day);
        assertThat(reopened.openedAt()).isEqualTo(opened.openedAt());
        assertThat(reopened.openingAmount()).isEqualByComparingTo("100.00");
        assertThat(reopened.salesCount()).isEqualTo(1);
        assertThat(reopened.expectedCash()).isEqualByComparingTo("110.00");
        assertThat(cashSessionService.getCurrentSession().id()).isEqualTo(id);
        assertThat(adjustments(id)).containsExactly("Faltante al cierre de caja #" + opened.sessionNumber() + "|5.00|ANULADO");
        assertThat(jdbc.queryForObject("SELECT void_reason FROM financial_transactions WHERE id = ?", String.class, adjustmentId(id, "ANULADO")))
            .startsWith("Reapertura de caja #" + opened.sessionNumber());
        // Its sales and movements count for its day
        SaleResponse sale = sellInCash(2);
        FinancialTransactionResponse expense = spend("15.00");
        assertThat(sale.operationDate()).isEqualTo(day);
        assertThat(expense.getOperationDate()).isEqualTo(day);
        assertThat(cashBalance()).isEqualByComparingTo("115.00");
        assertThat(cashSessionService.getCurrentSession().expectedCash()).isEqualByComparingTo("115.00");

        // Closes again, 5 over, with other cuts: everything of the session counts and Caja is what was counted
        CashSessionSummaryResponse reclosed = close("120.00", cut("100", 1), cut("20", 1));
        assertThat(reclosed.expectedCash()).isEqualByComparingTo("115.00");
        assertThat(reclosed.difference()).isEqualByComparingTo("5.00");
        assertThat(reclosed.closedBy()).isEqualTo("ana");
        assertThat(reclosed.closedAt()).isAfter(closed.closedAt());
        assertThat(cashBalance()).isEqualByComparingTo("120.00");
        assertThat(adjustments(id)).containsExactly(
            "Faltante al cierre de caja #" + opened.sessionNumber() + "|5.00|ANULADO",
            "Sobrante al cierre de caja #" + opened.sessionNumber() + "|5.00|ACTIVO");
        assertThat(jdbc.queryForObject("SELECT operation_date FROM financial_transactions WHERE id = ?", LocalDate.class, adjustmentId(id, "ACTIVO")))
            .isEqualTo(day);

        // The detail: only the new cuts, and the undone closing with its own
        CashSessionDetailResponse detail = cashSessionService.getSessionDetail(id);
        next();
        assertThat(detail.closingAmount()).isEqualByComparingTo("120.00");
        assertThat(detail.cuts()).extracting(c -> c.denomination().intValue()).containsExactlyInAnyOrder(100, 20);
        assertThat(detail.reopenings()).hasSize(1);
        CashSessionReopeningResponse first = detail.reopenings().getFirst();
        assertThat(first.reason()).isEqualTo("Faltó cargar ventas");
        assertThat(first.reopenedBy()).isEqualTo("luis");
        assertThat(first.reopenedAt()).isNotNull();
        assertThat(first.closingAmount()).isEqualByComparingTo("105.00");
        assertThat(first.closingComment()).isEqualTo("Cierre 105.00");
        assertThat(first.closedBy()).isEqualTo("ana");
        // The database keeps microseconds; on Linux the clock has nanoseconds
        assertThat(first.closedAt()).isCloseTo(closed.closedAt(), within(1, ChronoUnit.MILLIS));
        assertThat(first.difference()).isEqualByComparingTo("-5.00");
        assertThat(first.expectedCash()).isEqualByComparingTo("110.00");
        assertThat(first.cuts()).extracting(c -> c.denomination().intValue()).containsExactly(100, 5);

        // No row was deleted: the undone cuts stay, tied to their reopening
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_denomination_cuts WHERE cash_session_id = ?", Integer.class, id)).isEqualTo(4);

        // Twice: the detail shows both, oldest first, and the second one voids the new adjustment only
        CashSessionSummaryResponse again = reopen(id, "Otra vez");
        assertThat(again.status().name()).isEqualTo("ABIERTA");
        assertThat(cashBalance()).isEqualByComparingTo("115.00");
        CashSessionDetailResponse twice = cashSessionService.getSessionDetail(id);
        next();
        assertThat(twice.cuts()).isEmpty();
        assertThat(twice.reopenings()).extracting(CashSessionReopeningResponse::reason).containsExactly("Faltó cargar ventas", "Otra vez");
        assertThat(twice.reopenings().get(1).cuts()).extracting(c -> c.denomination().intValue()).containsExactly(100, 20);

        // Exact this time: no adjustment and Caja is what was counted
        CashSessionSummaryResponse exact = close("115.00");
        assertThat(exact.difference()).isEqualByComparingTo("0.00");
        assertThat(cashBalance()).isEqualByComparingTo("115.00");
        assertThat(adjustments(id)).hasSize(2).allSatisfy(row -> assertThat(row).endsWith("ANULADO"));
    }

    @Test
    @DisplayName("Recerrar sin cortes no muestra los del cierre anterior")
    void reclosingWithoutCuts_showsNoOldCuts() {
        UUID id = open("100.00", "Carga").id();
        close("100.00", cut("100", 1));
        reopen(id, "Error");

        close("100.00");

        CashSessionDetailResponse detail = cashSessionService.getSessionDetail(id);
        assertThat(detail.cuts()).isEmpty();
        assertThat(detail.reopenings().getFirst().cuts()).hasSize(1);
    }

    @Test
    @DisplayName("La caja inicial propuesta para la siguiente es el cierre nuevo")
    void nextProposal_isTheNewClosing() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        close("100.00");
        reopen(opened.id(), "Error");
        close("130.00");

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();
        assertThat(proposal.proposedAmount()).isEqualByComparingTo("130.00");
        assertThat(proposal.previousSessionNumber()).isEqualTo(opened.sessionNumber());

        // And the next session opens with it, without an adjustment: Caja is that amount
        day = day.plusDays(1);
        CashSessionSummaryResponse following = open("130.00", "Carga");
        assertThat(adjustments(following.id())).isEmpty();
        assertThat(cashBalance()).isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("El ajuste de apertura no se toca al reabrir")
    void openingAdjustment_isNotTouched() {
        CashSessionSummaryResponse opened = open("90.00", "Se sacaron 10");
        assertThat(cashBalance()).isEqualByComparingTo("90.00");
        close("95.00");
        assertThat(cashBalance()).isEqualByComparingTo("95.00");

        reopen(opened.id(), "Error");

        assertThat(cashBalance()).isEqualByComparingTo("90.00");
        assertThat(adjustments(opened.id())).containsExactlyInAnyOrder(
            "Diferencia al abrir la caja #" + opened.sessionNumber() + "|10.00|ACTIVO",
            "Sobrante al cierre de caja #" + opened.sessionNumber() + "|5.00|ANULADO");
    }

    @Test
    @DisplayName("Sin diferencia en el cierre ningún saldo cambia")
    void closingWithoutDifference_noBalanceChanges() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        sellInCash(1);
        close("110.00");
        assertThat(cashBalance()).isEqualByComparingTo("110.00");

        reopen(opened.id(), "Error");

        assertThat(cashBalance()).isEqualByComparingTo("110.00");
        assertThat(adjustments(opened.id())).isEmpty();
    }

    @Test
    @DisplayName("La reversión puede dejar Caja en negativo, porque vuelve a lo que era")
    void reversal_canLeaveCashNegative() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        spend("150.00");
        assertThat(cashBalance()).isEqualByComparingTo("-50.00");
        close("20.00");
        assertThat(cashBalance()).isEqualByComparingTo("20.00");

        reopen(opened.id(), "Error");

        assertThat(cashBalance()).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("Los reportes del día no cuentan el ajuste anulado y sí todo lo demás de la caja")
    void reports_leaveOutTheVoidedAdjustmentOnly() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        sellInCash(3);
        spend("7.00");
        close("100.00");
        reopen(opened.id(), "Error");

        SalesByDayReportResponse sales = reportService.getSalesByDay(day, day);
        assertThat(sales.totals().normalTotal()).isEqualByComparingTo("30.00");

        TransactionsByCategoryReportResponse.Month movements = reportService.getTransactionsByCategory(day, day).totals();
        // The expense of 7 only; the shortage of 23 is voided
        assertThat(movements.expenseTotal()).isEqualByComparingTo("-7.00");
        assertThat(movements.incomeTotal()).isEqualByComparingTo("30.00");

        DashboardResponse dashboard = dashboardService.getDashboard(day, day);
        assertThat(dashboard.current().sales()).isEqualByComparingTo("30.00");
        assertThat(dashboard.expenseTotal()).isEqualByComparingTo("7.00");
        assertThat(dashboard.now().cash().open()).isTrue();
        assertThat(dashboard.now().cash().operationDate()).isEqualTo(day);
    }

    @Test
    @DisplayName("El gasto anulado queda en la lista de la caja para la pestaña de gastos del POS")
    void voidedAdjustment_stayVisibleInTheSessionMovements() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        close("90.00");
        reopen(opened.id(), "Error");

        assertThat(financialTransactionService.getExpensesByCashSession(opened.id()))
            .filteredOn(tx -> tx.getDescription().startsWith("Faltante al cierre"))
            .singleElement()
            .satisfies(tx -> assertThat(tx.getStatus().name()).isEqualTo("ANULADO"));
    }

    @Test
    @DisplayName("Una venta y un gasto de la caja reabierta se anulan según las reglas de siempre")
    void reopenedSession_voidsItsOwnMovements() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        SaleResponse sale = sellInCash(1);
        FinancialTransactionResponse expense = spend("4.00");
        close("106.00");
        reopen(opened.id(), "Error");
        assertThat(cashBalance()).isEqualByComparingTo("106.00");

        saleService.voidSale(sale.id(), new VoidSaleRequest("Mal cobrada"), USER);
        next();
        financialTransactionService.voidCashExpense(expense.getId(),
            VoidFinancialTransactionRequest.builder().voidReason("Mal cargado").build(), USER);
        next();

        assertThat(cashBalance()).isEqualByComparingTo("100.00");
        assertThat(cashSessionService.getCurrentSession().expectedCash()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Finanzas y POS siguen rechazando anular el ajuste, también el nuevo de la caja recerrada")
    void newAdjustment_stillCannotBeVoidedByHand() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        close("90.00");
        reopen(opened.id(), "Error");
        close("95.00");
        UUID adjustment = adjustmentId(opened.id(), "ACTIVO");
        VoidFinancialTransactionRequest request = VoidFinancialTransactionRequest.builder().voidReason("A mano").build();

        assertThatThrownBy(() -> financialTransactionService.voidTransaction(adjustment, request, USER))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("ajustes de caja");
        assertThatThrownBy(() -> financialTransactionService.voidCashExpense(adjustment, request, USER))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Con una caja posterior, aunque esté cerrada, se rechaza; el historial ofrece solo la última")
    void laterSession_rejectsReopening() {
        CashSessionSummaryResponse opened = open("100.00", "Carga");
        close("100.00");
        day = day.plusDays(1);
        CashSessionSummaryResponse later = open("100.00", "Carga");
        assertThat(cashSessionService.getAllSessions()).noneMatch(CashSessionSummaryResponse::reopenable);
        close("100.00");

        assertThat(cashSessionService.getAllSessions()).filteredOn(CashSessionSummaryResponse::reopenable)
            .singleElement().extracting(CashSessionSummaryResponse::id).isEqualTo(later.id());
        assertThatThrownBy(() -> cashSessionService.reopenSession(opened.id(), new ReopenCashRequest("Error"), "luis"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Solo se puede reabrir la última caja.");
    }

    @Test
    @DisplayName("Una caja histórica se rechaza aunque sea la última por día")
    void historicalSession_rejected() {
        UUID importId = jdbc.queryForObject("INSERT INTO historical_imports (cutoff_date, imported_by) VALUES (?, ?) RETURNING id",
            UUID.class, day, USER);
        UUID historical = jdbc.queryForObject("INSERT INTO cash_sessions (status, opening_amount, opened_by, operation_date, closing_amount, "
            + "closed_at, expected_cash, difference, historical_import_id) VALUES ('CERRADA', 1, ?, ?, 1, now(), 1, 0, ?) RETURNING id",
            UUID.class, USER, day, importId);

        assertThatThrownBy(() -> cashSessionService.reopenSession(historical, new ReopenCashRequest("Error"), "luis"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Una caja histórica no se reabre.");
    }
}
