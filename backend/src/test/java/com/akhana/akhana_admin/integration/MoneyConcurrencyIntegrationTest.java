package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.OpenCashRequest;
import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.FinancialTransactionService;
import com.akhana.akhana_admin.service.SaleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
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
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T-14 / T-06: concurrent money operations against the real database. Threads need committed data, so this class
 * does not roll back: everything it creates is tagged with {@link #USER} and removed after each test. It is skipped
 * when the database already has an open cash session, so it never touches real work in progress.
 */
@SpringBootTest
class MoneyConcurrencyIntegrationTest {

    private static final String USER = "it-concurrency";
    private static final int THREADS = 8;

    @Autowired
    private CashSessionService cashSessionService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private FinancialTransactionService financialTransactionService;

    @Autowired
    private JdbcTemplate jdbc;

    private BigDecimal cashAccountBalanceBefore;

    @BeforeEach
    void requireNoOpenSession() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assumeFalse(open != null && open > 0, "La base ya tiene una caja abierta: se omite la prueba concurrente");
        List<BigDecimal> cash = jdbc.queryForList(
            "SELECT current_balance FROM financial_accounts WHERE is_cash_account = TRUE", BigDecimal.class);
        assumeTrue(cash.size() == 1, "La base no tiene la cuenta Caja");
        cashAccountBalanceBefore = cash.get(0);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM financial_transactions WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE created_by = ?)", USER);
        jdbc.update("DELETE FROM sales WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM cash_denomination_cuts WHERE cash_session_id IN (SELECT id FROM cash_sessions WHERE opened_by = ?)", USER);
        jdbc.update("DELETE FROM cash_sessions WHERE opened_by = ?", USER);
        jdbc.update("DELETE FROM products WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM categories WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM suppliers WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM financial_accounts WHERE created_by = ?", USER);
        if (cashAccountBalanceBefore != null) {
            jdbc.update("UPDATE financial_accounts SET current_balance = ? WHERE is_cash_account = TRUE", cashAccountBalanceBefore);
        }
    }

    @Test
    @DisplayName("Ventas simultáneas en efectivo: saldo de Caja y totales de la caja correctos, números de venta distintos")
    void concurrentSales_keepBalancesAndGetDistinctNumbers() throws Exception {
        UUID productId = createProduct(new BigDecimal("10.00"));
        // Opened with the proposed amount (the Caja balance), so no opening adjustment is recorded (CAJ-10)
        BigDecimal opening = cashSessionService.getOpeningProposal().proposedAmount();
        cashSessionService.openSession(new OpenCashRequest(opening, null), USER);

        List<Outcome<SaleResponse>> outcomes = runConcurrently(THREADS, i -> () -> saleService.registerSale(
            new SaleDetailRequest(List.of(new SaleItemRequest(productId, new BigDecimal("1"), null)), null, PaymentMethod.EFECTIVO,
                null, null, new BigDecimal("10.00"), null),
            USER));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(outcomes.stream().map(o -> o.value().saleNumber()).distinct()).hasSize(THREADS);

        BigDecimal salesTotal = new BigDecimal("10.00").multiply(BigDecimal.valueOf(THREADS));
        assertThat(cashAccountBalance()).isEqualByComparingTo(cashAccountBalanceBefore.add(salesTotal));
        var session = cashSessionService.getCurrentSession();
        assertThat(session.totalSalesCash()).isEqualByComparingTo(salesTotal);
        assertThat(session.totalSales()).isEqualByComparingTo(salesTotal);
        assertThat(session.expectedCash()).isEqualByComparingTo(opening.add(salesTotal));
    }

    @Test
    @DisplayName("Transferencias simultáneas en ambos sentidos entre dos cuentas: saldos correctos y sin deadlock")
    void concurrentTransfers_keepBothBalances() throws Exception {
        UUID accountA = createAccount("A", new BigDecimal("1000.00"));
        UUID accountB = createAccount("B", new BigDecimal("1000.00"));

        // Even threads move 7.00 from A to B, odd threads move 3.00 from B to A
        List<Outcome<Object>> outcomes = runConcurrently(THREADS, i -> () -> financialTransactionService.createTransaction(
            FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccountId(i % 2 == 0 ? accountA : accountB)
                .destinationAccountId(i % 2 == 0 ? accountB : accountA)
                .amount(i % 2 == 0 ? new BigDecimal("7.00") : new BigDecimal("3.00"))
                .description("Transferencia concurrente " + i)
                .build(),
            USER));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        int each = THREADS / 2;
        BigDecimal net = new BigDecimal("4.00").multiply(BigDecimal.valueOf(each));
        assertThat(accountBalance(accountA)).isEqualByComparingTo(new BigDecimal("1000.00").subtract(net));
        assertThat(accountBalance(accountB)).isEqualByComparingTo(new BigDecimal("1000.00").add(net));
    }

    @Test
    @DisplayName("Aperturas simultáneas de caja: solo una queda abierta y las demás se rechazan")
    void concurrentOpenings_leaveOnlyOneOpenSession() throws Exception {
        List<Outcome<Object>> outcomes = runConcurrently(THREADS, i -> () ->
            cashSessionService.openSession(new OpenCashRequest(new BigDecimal("50.00"), "Apertura " + i), USER));

        assertThat(outcomes.stream().filter(o -> o.error() == null)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.error() != null))
            .hasSize(THREADS - 1)
            .allSatisfy(o -> assertThat(o.error()).isInstanceOf(DuplicateResourceException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class))
            .isEqualTo(1);
    }

    private record Outcome<T>(T value, Throwable error) {}

    @SuppressWarnings("unchecked")
    private <T> List<Outcome<T>> runConcurrently(int threads, IntFunction<Callable<?>> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<?> call = task.apply(i);
            futures.add(pool.submit(() -> {
                start.await();
                return call.call();
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("las operaciones terminan sin quedar bloqueadas").isTrue();

        List<Outcome<T>> outcomes = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                outcomes.add(new Outcome<>((T) future.get(), null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome<>(null, e.getCause()));
            }
        }
        return outcomes;
    }

    private UUID createProduct(BigDecimal sellPrice) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID categoryId = jdbc.queryForObject(
            "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT categoria " + suffix, USER);
        UUID supplierId = jdbc.queryForObject(
            "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT proveedor " + suffix, "ITP-" + suffix, USER);
        return jdbc.queryForObject(
            "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, created_by) " +
            "VALUES (?, ?, ?, ?, 0.00, ?, ?, 0.00, ?) RETURNING id",
            UUID.class, "IT-" + suffix, "IT producto " + suffix, categoryId, supplierId, sellPrice, sellPrice, USER);
    }

    private UUID createAccount(String label, BigDecimal balance) {
        return jdbc.queryForObject(
            "INSERT INTO financial_accounts (name, initial_balance, current_balance, created_by) VALUES (?, ?, ?, ?) RETURNING id",
            UUID.class, "IT cuenta " + label + " " + UUID.randomUUID().toString().substring(0, 8), balance, balance, USER);
    }

    private BigDecimal accountBalance(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private BigDecimal cashAccountBalance() {
        return jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE is_cash_account = TRUE", BigDecimal.class);
    }
}
