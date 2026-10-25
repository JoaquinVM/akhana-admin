package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.service.CreditorService;
import com.akhana.akhana_admin.service.PurchaseService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DEU-01 to DEU-04 against PostgreSQL: V10 schema, unique name, single payment source and computed debts. */
@SpringBootTest
class CreditorRepositoryTest {

    @Autowired private CreditorRepository creditorRepository;
    @Autowired private PurchasePaymentRepository purchasePaymentRepository;
    @Autowired private CreditorRepaymentRepository creditorRepaymentRepository;
    @Autowired private CreditorService creditorService;
    @Autowired private PurchaseService purchaseService;
    @Autowired private FinancialTransactionRepository financialTransactionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @Transactional
    void nameIsUniqueIgnoringCaseExceptDeleted() {
        String name = "IT Mami " + UUID.randomUUID().toString().substring(0, 8);
        UUID first = insertCreditor(name, "ACTIVO");
        jdbc.update("UPDATE creditors SET status = 'ELIMINADO' WHERE id = ?", first);
        assertNotNull(insertCreditor(name.toLowerCase(), "ACTIVO"));
        assertTrue(creditorRepository.existsByNameIgnoreCaseAndStatusNot(name.toUpperCase(), CreditorStatus.ELIMINADO));

        // Last: PostgreSQL aborts the transaction after the violation
        assertThrows(DataIntegrityViolationException.class, () -> insertCreditor(name.toUpperCase(), "INACTIVO"));
    }

    @Test
    @Transactional
    void searchFiltersByStatusAndName() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID active = insertCreditor("IT Activa " + suffix, "ACTIVO");
        UUID inactive = insertCreditor("IT Inactiva " + suffix, "INACTIVO");
        UUID deleted = insertCreditor("IT Eliminada " + suffix, "ELIMINADO");

        List<UUID> byDefault = creditorRepository.search(null, suffix).stream().map(Creditor::getId).toList();
        assertEquals(List.of(active, inactive), byDefault);
        assertEquals(List.of(deleted),
                creditorRepository.search(CreditorStatus.ELIMINADO, suffix.toUpperCase()).stream().map(Creditor::getId).toList());
    }

    @Test
    @Transactional
    void personPaymentHasNoMovement() {
        UUID creditor = insertCreditor("IT Fuente " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        insertPersonPayment(insertPurchase(), creditor, "10.00", "ACTIVO");
        assertEquals(1, purchasePaymentRepository.findByCreditorId(creditor).size());
    }

    @Test
    @Transactional
    void paymentNeedsASource() {
        UUID purchase = insertPurchase();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO purchase_payments (purchase_id, amount, payment_date, created_by) "
                        + "VALUES (?, 10, CURRENT_TIMESTAMP, 'it-repo')", purchase));
    }

    @Test
    @Transactional
    void paymentHasOnlyOneSource() {
        UUID creditor = insertCreditor("IT Doble " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID purchase = insertPurchase();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO purchase_payments (purchase_id, account_id, creditor_id, amount, payment_date, created_by) "
                        + "VALUES (?, ?, ?, 10, CURRENT_TIMESTAMP, 'it-repo')", purchase, anyAccount(), creditor));
    }

    @Test
    @Transactional
    void accountPaymentNeedsItsMovement() {
        UUID purchase = insertPurchase();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO purchase_payments (purchase_id, account_id, amount, payment_date, created_by) "
                        + "VALUES (?, ?, 10, CURRENT_TIMESTAMP, 'it-repo')", purchase, anyAccount()));
    }

    @Test
    @Transactional
    void debtIsActivePaymentsMinusActiveRepayments() {
        UUID mami = insertCreditor("IT Deuda " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID other = insertCreditor("IT Sin deuda " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID purchase = insertPurchase();
        UUID account = anyAccount();

        insertPersonPayment(purchase, mami, "200.00", "ACTIVO");
        insertPersonPayment(purchase, mami, "100.00", "ACTIVO");
        insertPersonPayment(purchase, mami, "70.00", "ANULADO");
        insertRepayment(mami, account, "150.00", "ACTIVO");
        insertRepayment(mami, account, "20.00", "ANULADO");

        assertEquals(0, new BigDecimal("150.00").compareTo(creditorRepository.computeDebt(mami)));
        assertEquals(0, BigDecimal.ZERO.compareTo(creditorRepository.computeDebt(other)));

        BigDecimal fromList = creditorRepository.computeDebts().stream()
                .filter(row -> mami.equals(row[0]))
                .map(row -> (BigDecimal) row[1])
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(fromList));
        assertEquals(2, creditorRepaymentRepository.findByCreditorId(mami).size());
        assertNotNull(creditorRepository.findByIdForUpdate(mami).orElseThrow());
    }

    @Test
    @Transactional
    void repaymentExpenseLinksToItsRepayment() {
        UUID mami = insertCreditor("IT Vínculo " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID account = anyAccount();
        UUID repayment = insertRepayment(mami, account, "10.00", "ACTIVO");
        String insertTx = "INSERT INTO financial_transactions (type, source_account_id, amount, transaction_date, description, "
                + "creditor_repayment_id, created_by) VALUES ('GASTO_MANUAL', ?, 10.00, CURRENT_TIMESTAMP, 'IT', ?, 'it-repo')";
        jdbc.update(insertTx, account, repayment);

        // One expense per repayment
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insertTx, account, repayment));
    }

    @Test
    @Transactional
    void personPaysPurchaseAndIsRepaidThroughTheServices() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID account = jdbc.queryForObject("INSERT INTO financial_accounts (name, current_balance) VALUES (?, 1000) RETURNING id",
                UUID.class, "IT Bisa " + suffix);
        UUID supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT proveedor " + suffix, "ITS-" + suffix);
        UUID category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-repo') RETURNING id",
                UUID.class, "IT categoría " + suffix);
        UUID product = jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, sale_unit, created_by) "
                        + "VALUES (?, ?, ?, ?, 10.00, 15.00, 5.00, 50.00, 'UNIT', 'it-repo') RETURNING id",
                UUID.class, "ITP-" + suffix, "IT producto " + suffix, category, supplier);
        UUID mami = creditorService.createCreditor(new CreditorRequest("IT Mami " + suffix, null, null, null), "it-repo").id();

        PurchaseResponse purchase = purchaseService.registerPurchase(new PurchaseRequest(supplier, null,
                List.of(new PurchaseItemRequest(product, new BigDecimal("30"), new BigDecimal("10.00"), false, null)),
                null, null, false, null, null, null, null,
                new PurchasePaymentRequest(null, mami, new BigDecimal("200.00"), null)), "it-repo");
        assertEquals(PurchasePaymentStatus.A_CUENTA, purchase.paymentStatus());
        assertTrue(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.id()).isEmpty());

        CreditorHistoryResponse afterRepayment = creditorService.registerRepayment(mami,
                new CreditorRepaymentRequest(account, new BigDecimal("150.00"), "En mano"), "it-repo");
        entityManager.flush();
        assertEquals(0, new BigDecimal("50.00").compareTo(afterRepayment.creditor().debt()));
        UUID repaymentId = afterRepayment.entries().get(1).id();
        FinancialTransaction expense = financialTransactionRepository.findByCreditorRepaymentId(repaymentId).orElseThrow();
        assertEquals(FinancialCategorySystemKey.PURCHASE_PAYMENT, expense.getCategory().getSystemKey());
        assertEquals(0, new BigDecimal("850.00").compareTo(
                jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, account)));

        // The payment was partly repaid: it cannot be voided until the repayment is
        UUID paymentId = purchase.payments().get(0).id();
        assertThrows(IllegalStateException.class, () -> purchaseService.voidPayment(purchase.id(), paymentId,
                new VoidPurchaseRequest("Error"), "it-repo"));

        CreditorHistoryResponse afterVoid = creditorService.voidRepayment(mami, repaymentId, new VoidDebtRequest("Error"), "it-repo");
        entityManager.flush();
        assertEquals(0, new BigDecimal("200.00").compareTo(afterVoid.creditor().debt()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(
                jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, account)));

        purchaseService.voidPayment(purchase.id(), paymentId, new VoidPurchaseRequest("Error"), "it-repo");
        entityManager.flush();
        assertEquals(0, BigDecimal.ZERO.compareTo(creditorRepository.computeDebt(mami)));
    }

    private UUID anyAccount() {
        return jdbc.queryForObject("SELECT id FROM financial_accounts WHERE deleted_at IS NULL LIMIT 1", UUID.class);
    }

    private UUID insertCreditor(String name, String status) {
        return jdbc.queryForObject("INSERT INTO creditors (name, status, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, name, status);
    }

    private UUID insertPurchase() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID supplier = jdbc.queryForObject(
                "INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT proveedor " + suffix, "ITC-" + suffix);
        return jdbc.queryForObject(
                "INSERT INTO purchases (purchase_number, supplier_id, purchase_date, items_total, total_amount, created_by) "
                        + "VALUES (?, ?, CURRENT_DATE, 500, 500, 'it-repo') RETURNING id",
                UUID.class, "IT-DEU-" + suffix, supplier);
    }

    private void insertPersonPayment(UUID purchase, UUID creditor, String amount, String status) {
        jdbc.update("INSERT INTO purchase_payments (purchase_id, creditor_id, amount, payment_date, status, created_by) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, 'it-repo')", purchase, creditor, new BigDecimal(amount), status);
    }

    private UUID insertRepayment(UUID creditor, UUID account, String amount, String status) {
        return jdbc.queryForObject("INSERT INTO creditor_repayments (creditor_id, account_id, amount, repayment_date, status, created_by) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, 'it-repo') RETURNING id", UUID.class, creditor, account, new BigDecimal(amount), status);
    }
}
