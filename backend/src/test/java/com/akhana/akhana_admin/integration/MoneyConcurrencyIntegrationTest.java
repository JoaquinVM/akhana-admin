package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.CashOpeningProposalResponse;
import com.akhana.akhana_admin.dto.CashSessionSummaryResponse;
import com.akhana.akhana_admin.dto.CloseCashRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.OpenCashRequest;
import com.akhana.akhana_admin.dto.ReopenCashRequest;
import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.repository.CashSessionRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

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
    private CashSessionRepository cashSessionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
        String ownSessions = "(SELECT id FROM cash_sessions WHERE opened_by = ?)";
        jdbc.update("DELETE FROM cash_no_attention_days WHERE cash_session_id IN " + ownSessions, USER);
        jdbc.update("DELETE FROM cash_denomination_cuts WHERE cash_session_id IN " + ownSessions, USER);
        jdbc.update("DELETE FROM cash_session_reopenings WHERE cash_session_id IN " + ownSessions, USER);
        jdbc.update("DELETE FROM financial_transactions WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE created_by = ?)", USER);
        jdbc.update("DELETE FROM sales WHERE created_by = ?", USER);
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
        cashSessionService.openSession(openingOn(firstFreeDay(), opening), USER);

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
        OpenCashRequest request = openingOn(firstFreeDay(), new BigDecimal("50.00"));
        List<Outcome<Object>> outcomes = runConcurrently(THREADS, i -> () -> cashSessionService.openSession(request, USER));

        assertThat(outcomes.stream().filter(o -> o.error() == null)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.error() != null))
            .hasSize(THREADS - 1)
            .allSatisfy(o -> assertThat(o.error()).isInstanceOf(DuplicateResourceException.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("CAJ-12: dos aperturas simultáneas con días distintos: solo una lo logra")
    void concurrentOpeningsOfDifferentDays_leaveOnlyOne() throws Exception {
        LocalDate first = dayWithRoomForAnother();
        OpenCashRequest earlier = openingOn(first, new BigDecimal("50.00"));
        LocalDate laterDay = first.plusDays(1);
        // Opening the later day alone leaves the earlier one missing, unless it is a Sunday
        List<LocalDate> missing = cashSessionService.getOpeningProposal().unresolvedDays().stream()
            .filter(day -> day.isBefore(laterDay)).toList();
        OpenCashRequest later = new OpenCashRequest(new BigDecimal("50.00"), "Prueba concurrente", laterDay, missing, "Sin atención");

        List<Outcome<Object>> outcomes = runConcurrently(2, i -> () ->
            cashSessionService.openSession(i == 0 ? earlier : later, USER));

        assertThat(outcomes.stream().filter(o -> o.error() == null)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE opened_by = ?", Integer.class, USER)).isEqualTo(1);
        assertThat(openSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("CAJ-14: reaperturas simultáneas de una caja: solo una lo logra y el saldo de Caja se revierte una vez")
    void concurrentReopenings_revertOnce() throws Exception {
        CashSessionSummaryResponse session = openAndCloseWithShortage(firstFreeDay());
        BigDecimal afterClosing = cashAccountBalance();

        List<Outcome<CashSessionSummaryResponse>> outcomes = runConcurrently(THREADS, i -> () ->
            cashSessionService.reopenSession(session.id(), new ReopenCashRequest("Reapertura " + i), USER));

        assertThat(outcomes.stream().filter(o -> o.error() == null)).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o.error() != null))
            .hasSize(THREADS - 1)
            .allSatisfy(o -> assertThat(o.error()).isInstanceOf(IllegalStateException.class)
                .hasMessage("Ya hay una caja abierta; ciérrala antes de reabrir otra."));
        assertThat(openSessions()).isEqualTo(1);
        assertThat(cashAccountBalance()).isEqualByComparingTo(afterClosing.add(new BigDecimal("7.00")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_session_reopenings WHERE cash_session_id = ?", Integer.class, session.id()))
            .isEqualTo(1);
    }

    @Test
    @DisplayName("CAJ-14: una reabre mientras otra abre una caja nueva: solo una lo logra y nunca quedan dos abiertas")
    void reopeningAgainstOpening_onlyOneWins() throws Exception {
        LocalDate first = dayWithRoomForAnother();
        CashSessionSummaryResponse session = openAndCloseWithShortage(first);
        OpenCashRequest next = openingOn(first.plusDays(1), cashSessionService.getOpeningProposal().proposedAmount());

        List<Outcome<Object>> outcomes = runConcurrently(2, i -> () -> i == 0
            ? cashSessionService.reopenSession(session.id(), new ReopenCashRequest("Reapertura"), USER)
            : cashSessionService.openSession(next, USER));

        assertThat(outcomes.stream().filter(o -> o.error() == null)).hasSize(1);
        assertThat(openSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("CAJ-14: reabrir mientras otra abre y cierra una caja nueva: la reapertura espera y ve que ya no es la última")
    void reopeningAgainstOpenAndClose_waitsAndIsRejected() throws Exception {
        LocalDate first = dayWithRoomForAnother();
        CashSessionSummaryResponse session = openAndCloseWithShortage(first);
        BigDecimal proposed = cashSessionService.getOpeningProposal().proposedAmount();
        OpenCashRequest next = openingOn(first.plusDays(1), proposed);

        CountDownLatch locked = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> opener = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                cashSessionRepository.lockCashOpening();
                locked.countDown();
                sleep();
                cashSessionService.openSession(next, USER);
                cashSessionService.closeCurrentSession(new CloseCashRequest(proposed, null, List.of(), null, null), USER);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> reopener = pool.submit(() -> {
                try {
                    cashSessionService.reopenSession(session.id(), new ReopenCashRequest("Reapertura"), USER);
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });

            opener.get(20, TimeUnit.SECONDS);
            assertThat(reopener.get(20, TimeUnit.SECONDS)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Solo se puede reabrir la última caja.");
        } finally {
            pool.shutdownNow();
        }
        assertThat(openSessions()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM cash_sessions WHERE id = ?", String.class, session.id())).isEqualTo("CERRADA");
    }

    private static void sleep() {
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int openSessions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
    }

    // The first day after the last session never leaves a day missing, and it is what any database allows
    private LocalDate firstFreeDay() {
        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();
        LocalDate day = proposal.minDay() != null ? proposal.minDay() : proposal.today();
        assumeFalse(day.isAfter(proposal.today()), "La base ya tiene la caja de hoy: se omite la prueba concurrente");
        return day;
    }

    // Two days are needed: a session on this one and a later one right after it
    private LocalDate dayWithRoomForAnother() {
        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();
        LocalDate day = proposal.minDay() != null ? proposal.minDay() : proposal.today().minusDays(3);
        assumeFalse(day.plusDays(1).isAfter(proposal.today()), "La base no deja dos días libres: se omite la prueba concurrente");
        return day;
    }

    private static OpenCashRequest openingOn(LocalDate day, BigDecimal amount) {
        return new OpenCashRequest(amount, "Prueba concurrente", day, null, null);
    }

    private CashSessionSummaryResponse openAndCloseWithShortage(LocalDate day) {
        cashSessionService.openSession(openingOn(day, new BigDecimal("100.00")), USER);
        return cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal("93.00"), "Cierre con faltante", List.of(), null, null), USER);
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
