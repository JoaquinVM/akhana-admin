package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.CashOpeningProposalResponse;
import com.akhana.akhana_admin.dto.OpenCashRequest;
import com.akhana.akhana_admin.dto.PurchaseItemRequest;
import com.akhana.akhana_admin.dto.PurchaseRequest;
import com.akhana.akhana_admin.dto.PurchaseResponse;
import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.PurchaseService;
import com.akhana.akhana_admin.service.SaleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POS-18, COM-27: the same sale or purchase sent several times at once, with one client request id, is recorded once
 * and every request gets it back. Threads need committed data, so this class does not roll back: everything it creates
 * is tagged with {@link #USER} and removed after each test.
 */
@SpringBootTest
class ClientRequestIdempotencyIntegrationTest {

    private static final String USER = "it-idempotency";
    private static final int THREADS = 6;

    @Autowired
    private CashSessionService cashSessionService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private JdbcTemplate jdbc;

    private BigDecimal cashAccountBalanceBefore;

    @BeforeEach
    void requireNoOpenSession() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assertThat(open).as("La base ya tiene una caja abierta").isZero();
        List<BigDecimal> cash = jdbc.queryForList(
            "SELECT current_balance FROM financial_accounts WHERE is_cash_account = TRUE", BigDecimal.class);
        assertThat(cash).as("La base no tiene la cuenta Caja").hasSize(1);
        cashAccountBalanceBefore = cash.get(0);
    }

    @AfterEach
    void cleanUp() {
        String ownSessions = "(SELECT id FROM cash_sessions WHERE opened_by = ?)";
        jdbc.update("DELETE FROM cash_no_attention_days WHERE cash_session_id IN " + ownSessions, USER);
        jdbc.update("DELETE FROM financial_transactions WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE created_by = ?)", USER);
        jdbc.update("DELETE FROM sales WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM purchase_items WHERE purchase_id IN (SELECT id FROM purchases WHERE created_by = ?)", USER);
        jdbc.update("DELETE FROM purchases WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM cash_sessions WHERE opened_by = ?", USER);
        jdbc.update("DELETE FROM products WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM categories WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM suppliers WHERE created_by = ?", USER);
        if (cashAccountBalanceBefore != null) {
            jdbc.update("UPDATE financial_accounts SET current_balance = ? WHERE is_cash_account = TRUE", cashAccountBalanceBefore);
        }
    }

    @Test
    @DisplayName("La misma venta enviada a la vez con un mismo id: una sola venta, el dinero entra una vez y todos la reciben")
    void sameSaleAtOnce_isRecordedOnce() throws Exception {
        Fixture fixture = createProduct(new BigDecimal("10.00"));
        BigDecimal opening = cashSessionService.getOpeningProposal().proposedAmount();
        cashSessionService.openSession(new OpenCashRequest(opening, "Prueba idempotencia", firstFreeDay(), null, null), USER);
        UUID clientRequestId = UUID.randomUUID();
        SaleDetailRequest request = new SaleDetailRequest(
            List.of(new SaleItemRequest(fixture.productId(), new BigDecimal("2"), null)), null, PaymentMethod.EFECTIVO,
            null, null, new BigDecimal("20.00"), null, null, null, null, clientRequestId);

        List<Outcome<SaleResponse>> outcomes = runConcurrently(() -> saleService.registerSale(request, USER));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(outcomes.stream().map(o -> o.value().id()).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales WHERE client_request_id = ?", Integer.class, clientRequestId))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE created_by = ? AND type = 'VENTA_INGRESO'",
            Integer.class, USER)).isEqualTo(1);
        assertThat(cashAccountBalance()).isEqualByComparingTo(cashAccountBalanceBefore.add(new BigDecimal("20.00")));
        var session = cashSessionService.getCurrentSession();
        assertThat(session.totalSales()).isEqualByComparingTo("20.00");
        assertThat(session.expectedCash()).isEqualByComparingTo(opening.add(new BigDecimal("20.00")));

        // A later retry, as after a lost answer, also gets the same sale and moves no money
        SaleResponse retried = saleService.registerSale(request, USER);
        assertThat(retried.id()).isEqualTo(outcomes.get(0).value().id());
        assertThat(cashAccountBalance()).isEqualByComparingTo(cashAccountBalanceBefore.add(new BigDecimal("20.00")));
    }

    @Test
    @DisplayName("El mismo pedido enviado a la vez con un mismo id: el índice único deja uno solo y todos lo reciben")
    void samePurchaseAtOnce_isRecordedOnce() throws Exception {
        Fixture fixture = createProduct(new BigDecimal("10.00"));
        UUID clientRequestId = UUID.randomUUID();
        // No payment and no shipping: nothing is locked, so the requests really race to the unique index
        PurchaseRequest request = new PurchaseRequest(fixture.supplierId(), null,
            List.of(new PurchaseItemRequest(fixture.productId(), new BigDecimal("3"), new BigDecimal("4.00"), false, null)),
            null, null, false, null, null, null, null, null, null, null, clientRequestId);

        List<Outcome<PurchaseResponse>> outcomes = runConcurrently(() -> purchaseService.registerPurchase(request, USER));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(outcomes.stream().map(o -> o.value().id()).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchases WHERE client_request_id = ?", Integer.class, clientRequestId))
            .isEqualTo(1);
        assertThat(outcomes.get(0).value().totalAmount()).isEqualByComparingTo("12.00");
    }

    @Test
    @DisplayName("Ventas sin id (clientes viejos) siguen registrándose cada una")
    void salesWithoutId_areStillRecorded() {
        Fixture fixture = createProduct(new BigDecimal("5.00"));
        BigDecimal opening = cashSessionService.getOpeningProposal().proposedAmount();
        cashSessionService.openSession(new OpenCashRequest(opening, "Prueba idempotencia", firstFreeDay(), null, null), USER);
        SaleDetailRequest request = new SaleDetailRequest(
            List.of(new SaleItemRequest(fixture.productId(), new BigDecimal("1"), null)), null, PaymentMethod.EFECTIVO,
            null, null, new BigDecimal("5.00"), null);

        SaleResponse first = saleService.registerSale(request, USER);
        SaleResponse second = saleService.registerSale(request, USER);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales WHERE created_by = ? AND client_request_id IS NULL",
            Integer.class, USER)).isEqualTo(2);
    }

    private LocalDate firstFreeDay() {
        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();
        LocalDate day = proposal.minDay() != null ? proposal.minDay() : proposal.today();
        assertThat(day).as("La base ya tiene la caja de hoy").isBeforeOrEqualTo(proposal.today());
        return day;
    }

    private record Outcome<T>(T value, Throwable error) {}

    private <T> List<Outcome<T>> runConcurrently(Callable<T> call) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return call.call();
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("las operaciones terminan sin quedar bloqueadas").isTrue();

        List<Outcome<T>> outcomes = new ArrayList<>();
        for (Future<T> future : futures) {
            try {
                outcomes.add(new Outcome<>(future.get(), null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome<>(null, e.getCause()));
            }
        }
        return outcomes;
    }

    private record Fixture(UUID productId, UUID supplierId) {}

    private Fixture createProduct(BigDecimal sellPrice) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID categoryId = jdbc.queryForObject(
            "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT categoria " + suffix, USER);
        UUID supplierId = jdbc.queryForObject(
            "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT proveedor " + suffix, "ITP-" + suffix, USER);
        UUID productId = jdbc.queryForObject(
            "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, created_by) " +
            "VALUES (?, ?, ?, ?, 0.00, ?, ?, 0.00, ?) RETURNING id",
            UUID.class, "IT-" + suffix, "IT producto " + suffix, categoryId, supplierId, sellPrice, sellPrice, USER);
        return new Fixture(productId, supplierId);
    }

    private BigDecimal cashAccountBalance() {
        return jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE is_cash_account = TRUE", BigDecimal.class);
    }
}
