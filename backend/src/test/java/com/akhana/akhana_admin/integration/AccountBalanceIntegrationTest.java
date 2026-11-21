package com.akhana.akhana_admin.integration;

import com.akhana.akhana_admin.dto.CashOpeningProposalResponse;
import com.akhana.akhana_admin.dto.CashTransferRequest;
import com.akhana.akhana_admin.dto.FinancialAccountRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.OpenCashRequest;
import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.model.CashTransferDirection;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.service.CashSessionService;
import com.akhana.akhana_admin.service.FinancialAccountService;
import com.akhana.akhana_admin.service.FinancialTransactionService;
import com.akhana.akhana_admin.service.SaleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Account balances against the real database: an edit or a cash transfer that runs while a sale or a movement is
 * still open does not write back an old balance (T-14); a void never leaves an account negative (POS-07, FIN-06);
 * and the V35 rules on amounts and account names. Threads need committed data, so nothing rolls back: everything is
 * tagged with {@link #USER} and removed after each test, and the Caja and QR accounts are put back as they were.
 */
@SpringBootTest
class AccountBalanceIntegrationTest {

    private static final String USER = "it-account-balance";

    @Autowired private CashSessionService cashSessionService;
    @Autowired private SaleService saleService;
    @Autowired private FinancialTransactionService financialTransactionService;
    @Autowired private FinancialAccountService financialAccountService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;

    private UUID cashId;
    private BigDecimal cashBefore;
    private UUID qrId;
    private BigDecimal qrBefore;

    @BeforeEach
    void rememberAccounts() {
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM cash_sessions WHERE status = 'ABIERTA'", Integer.class);
        assertThat(open).as("La base ya tiene una caja abierta").isZero();
        cashId = jdbc.queryForObject("SELECT id FROM financial_accounts WHERE is_cash_account = TRUE", UUID.class);
        cashBefore = balance(cashId);
        qrId = jdbc.queryForObject(
            "SELECT id FROM financial_accounts WHERE is_qr_account = TRUE AND deleted_at IS NULL", UUID.class);
        qrBefore = balance(qrId);
    }

    @AfterEach
    void cleanUp() {
        String ownSessions = "(SELECT id FROM cash_sessions WHERE opened_by = ?)";
        jdbc.update("DELETE FROM cash_no_attention_days WHERE cash_session_id IN " + ownSessions, USER);
        jdbc.update("DELETE FROM cash_denomination_cuts WHERE cash_session_id IN " + ownSessions, USER);
        jdbc.update("DELETE FROM financial_transactions WHERE created_by = ? OR source_account_id IN "
            + "(SELECT id FROM financial_accounts WHERE created_by = ?)", USER, USER);
        jdbc.update("DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE created_by = ?)", USER);
        jdbc.update("DELETE FROM sales WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM cash_sessions WHERE opened_by = ?", USER);
        jdbc.update("DELETE FROM products WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM categories WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM financial_categories WHERE created_by = ?", USER);
        jdbc.update("DELETE FROM suppliers WHERE created_by = ?", USER);
        // The QR mark goes back to its account before the test accounts are removed (FIN-03)
        jdbc.update("UPDATE financial_accounts SET is_qr_account = FALSE WHERE is_qr_account = TRUE");
        jdbc.update("UPDATE financial_accounts SET is_qr_account = TRUE, current_balance = ? WHERE id = ?", qrBefore, qrId);
        jdbc.update("UPDATE financial_accounts SET current_balance = ? WHERE id = ?", cashBefore, cashId);
        jdbc.update("DELETE FROM financial_accounts WHERE created_by = ?", USER);
    }

    // ---- 1. Editing an account does not overwrite its balance ----

    @Test
    @DisplayName("Editar una cuenta mientras entra un ingreso: el saldo final incluye el ingreso")
    void updateAccount_whileAnIncomeIsOpen_keepsTheIncome() throws Exception {
        UUID account = createAccount("Edicion", "100.00");
        UUID category = jdbc.queryForObject(
            "INSERT INTO financial_categories (name, type, created_by) VALUES (?, 'INGRESO', ?) RETURNING id",
            UUID.class, "IT ingreso " + suffix(), USER);

        whileHeldOpen(
            () -> financialTransactionService.createTransaction(FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.INGRESO_MANUAL).sourceAccountId(account).categoryId(category)
                .amount(new BigDecimal("50.00")).description("Ingreso concurrente").build(), USER),
            () -> financialAccountService.updateAccount(account, accountRequest("IT cuenta renombrada " + suffix(), null), USER));

        assertThat(balance(account)).isEqualByComparingTo("150.00");
        assertThat(jdbc.queryForObject("SELECT name FROM financial_accounts WHERE id = ?", String.class, account))
            .startsWith("IT cuenta renombrada");
    }

    @Test
    @DisplayName("Pasar la marca QR mientras entra una venta QR: la cuenta QR anterior conserva la venta")
    void setQrAccount_whileAQrSaleIsOpen_keepsTheSale() throws Exception {
        UUID product = createProduct(new BigDecimal("10.00"));
        UUID other = createAccount("Nueva QR", "0.00");
        openSession();

        whileHeldOpen(
            () -> saleService.registerSale(sale(product, PaymentMethod.QR, null, new BigDecimal("10.00")), USER),
            () -> financialAccountService.setQrAccount(other, USER));

        assertThat(balance(qrId)).isEqualByComparingTo(qrBefore.add(new BigDecimal("10.00")));
        assertThat(jdbc.queryForObject("SELECT is_qr_account FROM financial_accounts WHERE id = ?", Boolean.class, other)).isTrue();
    }

    @Test
    @DisplayName("Editar otra cuenta marcándola QR mientras entra una venta QR: la cuenta QR anterior conserva la venta")
    void updateAccountToQr_whileAQrSaleIsOpen_keepsTheSale() throws Exception {
        UUID product = createProduct(new BigDecimal("10.00"));
        UUID other = createAccount("Editada QR", "0.00");
        openSession();

        whileHeldOpen(
            () -> saleService.registerSale(sale(product, PaymentMethod.QR, null, new BigDecimal("10.00")), USER),
            () -> financialAccountService.updateAccount(other, accountRequest("IT cuenta QR " + suffix(), true), USER));

        assertThat(balance(qrId)).isEqualByComparingTo(qrBefore.add(new BigDecimal("10.00")));
    }

    // ---- 2. A cash transfer uses the balance read after the lock ----

    @Test
    @DisplayName("Retiro de caja mientras entra una venta en efectivo: Caja suma la venta y resta el retiro")
    void cashWithdrawal_whileACashSaleIsOpen_keepsBoth() throws Exception {
        UUID product = createProduct(new BigDecimal("10.00"));
        UUID account = createAccount("Destino retiro", "0.00");
        openSession();
        BigDecimal cashAtOpening = balance(cashId);

        whileHeldOpen(
            () -> saleService.registerSale(sale(product, PaymentMethod.EFECTIVO, null, null), USER),
            () -> financialTransactionService.createCashTransfer(CashTransferRequest.builder()
                .direction(CashTransferDirection.RETIRO).accountId(account).amount(new BigDecimal("4.00"))
                .comment("Retiro concurrente").build(), USER));

        assertThat(balance(cashId)).isEqualByComparingTo(cashAtOpening.add(new BigDecimal("6.00")));
        assertThat(balance(account)).isEqualByComparingTo("4.00");
    }

    // ---- 4. A void never leaves an account negative; 0.00 is allowed ----

    @Test
    @DisplayName("POS-07: anular una venta en efectivo que deja Caja justo en 0,00 se permite")
    void voidCashSale_leavingZero_passes() {
        UUID product = createProduct(new BigDecimal("10.00"));
        UUID account = createAccount("Retiro anulacion", "0.00");
        openSession();
        SaleResponse sold = saleService.registerSale(sale(product, PaymentMethod.EFECTIVO, null, null), USER);
        withdrawCashDownTo(account, new BigDecimal("10.00"));

        saleService.voidSale(sold.id(), new VoidSaleRequest("Error"), USER);

        assertThat(balance(cashId)).isEqualByComparingTo("0.00");
        assertThat(saleStatus(sold.id())).isEqualTo("ANULADA");
    }

    @Test
    @DisplayName("POS-07: anular una venta en efectivo que dejaría Caja en negativo se rechaza y no anula nada")
    void voidCashSale_leavingNegative_isRejected() {
        UUID product = createProduct(new BigDecimal("10.00"));
        UUID account = createAccount("Retiro anulacion", "0.00");
        openSession();
        SaleResponse sold = saleService.registerSale(sale(product, PaymentMethod.EFECTIVO, null, null), USER);
        withdrawCashDownTo(account, new BigDecimal("9.99"));

        assertThatThrownBy(() -> saleService.voidSale(sold.id(), new VoidSaleRequest("Error"), USER))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No se puede anular: la cuenta Caja quedaría con saldo negativo (saldo disponible: Bs 9,99). "
                + "Anula primero los gastos o transferencias que usaron ese dinero.");
        assertThat(balance(cashId)).isEqualByComparingTo("9.99");
        assertThat(saleStatus(sold.id())).isEqualTo("COMPLETADA");
    }

    @Test
    @DisplayName("POS-07: anular una venta QR que deja la cuenta QR justo en 0,00 se permite")
    void voidQrSale_leavingZero_passes() {
        UUID product = createProduct(new BigDecimal("10.00"));
        UUID account = createAccount("Desde QR", "0.00");
        openSession();
        SaleResponse sold = saleService.registerSale(sale(product, PaymentMethod.QR, null, new BigDecimal("10.00")), USER);
        transferQrDownTo(account, new BigDecimal("10.00"));

        saleService.voidSale(sold.id(), new VoidSaleRequest("Error"), USER);

        assertThat(balance(qrId)).isEqualByComparingTo("0.00");
        assertThat(saleStatus(sold.id())).isEqualTo("ANULADA");
    }

    @Test
    @DisplayName("POS-07: anular una venta QR que dejaría la cuenta QR en negativo se rechaza y no anula nada")
    void voidQrSale_leavingNegative_isRejected() {
        UUID product = createProduct(new BigDecimal("10.00"));
        UUID account = createAccount("Desde QR", "0.00");
        openSession();
        SaleResponse sold = saleService.registerSale(sale(product, PaymentMethod.QR, null, new BigDecimal("10.00")), USER);
        transferQrDownTo(account, new BigDecimal("9.99"));
        String qrName = jdbc.queryForObject("SELECT name FROM financial_accounts WHERE id = ?", String.class, qrId);

        assertThatThrownBy(() -> saleService.voidSale(sold.id(), new VoidSaleRequest("Error"), USER))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No se puede anular: la cuenta " + qrName + " quedaría con saldo negativo (saldo disponible: Bs 9,99). "
                + "Anula primero los gastos o transferencias que usaron ese dinero.");
        assertThat(balance(qrId)).isEqualByComparingTo("9.99");
        assertThat(saleStatus(sold.id())).isEqualTo("COMPLETADA");
    }

    // ---- 6. Migration V35 ----

    @Test
    @DisplayName("V35: un movimiento con monto 0 o negativo se rechaza; el saldo inicial en 0,00 sí entra")
    void amountMustBePositive_exceptAnEmptyOpeningBalance() {
        UUID account = createAccount("Montos", "0.00");
        String insert = "INSERT INTO financial_transactions (type, source_account_id, amount, transaction_date, operation_date, description, created_by) "
            + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_DATE, 'Prueba V35', ?)";

        assertThatThrownBy(() -> jdbc.update(insert, "GASTO_MANUAL", account, new BigDecimal("0.00"), USER))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("chk_financial_transactions_amount_positive");
        assertThatThrownBy(() -> jdbc.update(insert, "INGRESO_MANUAL", account, new BigDecimal("-1.00"), USER))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("chk_financial_transactions_amount_positive");
        assertThatThrownBy(() -> jdbc.update(insert, "SALDO_INICIAL", account, new BigDecimal("-0.01"), USER))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.update(insert, "SALDO_INICIAL", account, new BigDecimal("0.00"), USER)).isEqualTo(1);
        assertThat(jdbc.update(insert, "GASTO_MANUAL", account, new BigDecimal("0.01"), USER)).isEqualTo(1);
    }

    @Test
    @DisplayName("V35, FIN-01: el nombre de una cuenta eliminada se reutiliza; dos activas no comparten nombre sin importar mayúsculas")
    void accountName_isReusableAfterDelete_andUniqueAmongActive() {
        String name = "IT cuenta reusada " + suffix();
        FinancialAccountRequest request = accountRequest(name, null);
        request.setInitialBalance(BigDecimal.ZERO);
        UUID first = financialAccountService.createAccount(request, USER).getId();
        financialAccountService.deleteAccount(first, USER);

        UUID second = financialAccountService.createAccount(accountRequest(name.toUpperCase(), null), USER).getId();
        assertThat(second).isNotEqualTo(first);

        // The index itself, below the service check
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO financial_accounts (name, created_by) VALUES (?, ?)", name.toLowerCase(), USER))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uq_financial_accounts_name_active");
    }

    // ---- helpers ----

    /**
     * Runs {@code first} in a transaction that stays open (holding its locks) for a while after it is done, and
     * starts {@code second} meanwhile: the case where the second used to read the balance before the first commits.
     */
    private void whileHeldOpen(Runnable first, Runnable second) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                first.run();
                done.countDown();
                sleep();
            }));
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
            Future<?> other = pool.submit(second);
            holder.get(20, TimeUnit.SECONDS);
            other.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void withdrawCashDownTo(UUID account, BigDecimal target) {
        BigDecimal amount = balance(cashId).subtract(target);
        if (amount.signum() > 0) {
            financialTransactionService.createCashTransfer(CashTransferRequest.builder()
                .direction(CashTransferDirection.RETIRO).accountId(account).amount(amount).build(), USER);
        }
        assertThat(balance(cashId)).isEqualByComparingTo(target);
    }

    private void transferQrDownTo(UUID account, BigDecimal target) {
        BigDecimal amount = balance(qrId).subtract(target);
        if (amount.signum() > 0) {
            financialTransactionService.createTransaction(FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA).sourceAccountId(qrId).destinationAccountId(account)
                .amount(amount).description("Vaciar QR").build(), USER);
        }
        assertThat(balance(qrId)).isEqualByComparingTo(target);
    }

    private static SaleDetailRequest sale(UUID product, PaymentMethod method, BigDecimal cash, BigDecimal qr) {
        return new SaleDetailRequest(List.of(new SaleItemRequest(product, new BigDecimal("1"), null)), null, method,
            cash, qr, method == PaymentMethod.EFECTIVO ? new BigDecimal("10.00") : null, null);
    }

    // Opened with the proposed amount (the Caja balance), so no opening adjustment is recorded (CAJ-10)
    private void openSession() {
        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();
        LocalDate day = proposal.minDay() != null ? proposal.minDay() : proposal.today();
        assertThat(day).as("La base ya tiene la caja de hoy").isBeforeOrEqualTo(proposal.today());
        cashSessionService.openSession(new OpenCashRequest(proposal.proposedAmount(), "Prueba saldos", day, null, null), USER);
    }

    private FinancialAccountRequest accountRequest(String name, Boolean isQr) {
        FinancialAccountRequest request = new FinancialAccountRequest();
        request.setName(name);
        request.setColor("#123456");
        request.setIsQrAccount(isQr);
        return request;
    }

    private UUID createAccount(String label, String balance) {
        BigDecimal amount = new BigDecimal(balance);
        return jdbc.queryForObject(
            "INSERT INTO financial_accounts (name, initial_balance, current_balance, created_by) VALUES (?, ?, ?, ?) RETURNING id",
            UUID.class, "IT cuenta " + label + " " + suffix(), amount, amount, USER);
    }

    private UUID createProduct(BigDecimal sellPrice) {
        String suffix = suffix();
        UUID categoryId = jdbc.queryForObject(
            "INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', ?) RETURNING id",
            UUID.class, "IT categoria " + suffix, USER);
        UUID supplierId = jdbc.queryForObject(
            "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, ?) RETURNING id",
            UUID.class, "IT proveedor " + suffix, "ITP-" + suffix, USER);
        return jdbc.queryForObject(
            "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, created_by) "
                + "VALUES (?, ?, ?, ?, 0.00, ?, ?, 0.00, ?) RETURNING id",
            UUID.class, "IT-" + suffix, "IT producto " + suffix, categoryId, supplierId, sellPrice, sellPrice, USER);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private String saleStatus(UUID saleId) {
        return jdbc.queryForObject("SELECT status FROM sales WHERE id = ?", String.class, saleId);
    }
}
