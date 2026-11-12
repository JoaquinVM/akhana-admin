package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.*;
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

/** Shipping of a purchase paid by a person (COM-02, COM-12, DEU-02, DEU-04) against PostgreSQL, through the services. */
@SpringBootTest
class ShippingPaidByPersonIntegrationTest {

    @Autowired private PurchaseService purchaseService;
    @Autowired private CreditorService creditorService;
    @Autowired private CreditorRepository creditorRepository;
    @Autowired private FinancialTransactionRepository financialTransactionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    private UUID bisa;
    private UUID supplier;
    private UUID product;
    private UUID mami;
    private String mamiName;

    @Test
    @Transactional
    void personPaysTheShippingAndItFollowsTheEditsAndTheVoid() {
        setUp();

        // Registered: no movement, accounts untouched, the debt grows and the payment status ignores it
        PurchaseResponse purchase = purchaseService.registerPurchase(new PurchaseRequest(supplier, null,
                List.of(new PurchaseItemRequest(product, new BigDecimal("30"), new BigDecimal("10.00"), false, null)),
                new BigDecimal("25.00"), null, false, null, null, null, null, null, null, mami), "it-ship");
        entityManager.flush();
        assertTrue(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.id()).isEmpty());
        assertBalance("1000.00", bisa);
        assertDebt("25.00");
        assertEquals(PurchasePaymentStatus.POR_PAGAR, purchase.paymentStatus());
        assertEquals(0, new BigDecimal("300.00").compareTo(purchase.balance()));
        assertEquals(mamiName, purchaseService.getPurchaseById(purchase.id()).shippingCreditorName());

        CreditorHistoryResponse history = creditorService.getHistory(mami);
        assertEquals(1, history.entries().size());
        CreditorHistoryResponse.Entry shipping = history.entries().get(0);
        assertEquals(CreditorHistoryResponse.EntryType.SHIPPING, shipping.type());
        assertEquals(purchase.purchaseNumber(), shipping.purchaseNumber());
        assertNotNull(shipping.supplierName());
        assertEquals(0, new BigDecimal("25.00").compareTo(shipping.balanceAfter()));

        // Mami → Bisa: her shipping is voided with the reason and Bisa pays a Transporte expense
        PurchaseResponse edited = purchaseService.updatePurchase(purchase.id(), edit(purchase, "25.00", bisa, null,
                "Lo pagó la tienda"), "it-ship");
        entityManager.flush();
        assertDebt("0.00");
        assertBalance("975.00", bisa);
        assertEquals("IT Bisa " + suffix(), edited.shippingAccountName());
        assertNull(edited.shippingCreditorId());
        assertTrue(edited.revisions().get(0).changes().contains("Envío: Bs 25,00 (" + mamiName + ") → Bs 25,00 (IT Bisa "),
                edited.revisions().get(0).changes());
        history = creditorService.getHistory(mami);
        assertTrue(history.entries().get(0).voided());
        assertEquals("Lo pagó la tienda", history.entries().get(0).voidReason());
        assertEquals(0, BigDecimal.ZERO.compareTo(history.entries().get(0).balanceAfter()));

        // Bisa → Mami Bs 30: Bisa gets its money back and the debt is the new shipping
        edited = purchaseService.updatePurchase(purchase.id(), edit(edited, "30.00", null, mami, "Era de Mami"), "it-ship");
        entityManager.flush();
        assertBalance("1000.00", bisa);
        assertDebt("30.00");
        assertEquals(mamiName, edited.shippingCreditorName());

        // Repaid by ticking it (062): moving it away from her, changing it or voiding the purchase is rejected
        UUID chargeId = creditorService.getHistory(mami).entries().stream()
                .filter(e -> e.type() == CreditorHistoryResponse.EntryType.SHIPPING && !e.voided())
                .findFirst().orElseThrow().id();
        CreditorHistoryResponse repaid = creditorService.registerRepayment(mami, new CreditorRepaymentRequest(bisa,
                new BigDecimal("30.00"), "En mano", List.of(new CreditorRepaymentRequest.Item(
                        CreditorHistoryResponse.EntryType.SHIPPING, chargeId))), "it-ship");
        entityManager.flush();
        assertDebt("0.00");
        PurchaseResponse current = edited;
        String repaidMessage = "Ya se devolvió a " + mamiName + " parte de este envío; anule primero la devolución.";
        IllegalStateException toAccount = assertThrows(IllegalStateException.class, () -> purchaseService.updatePurchase(
                purchase.id(), edit(current, "30.00", bisa, null, "Cambio"), "it-ship"));
        assertEquals(repaidMessage, toAccount.getMessage());
        // DEU-03 (062): still hers and higher is also rejected while it stands as paid, though the debt would be 5
        IllegalStateException higher = assertThrows(IllegalStateException.class, () -> purchaseService.updatePurchase(
                purchase.id(), edit(current, "35.00", null, mami, "Subió el envío"), "it-ship"));
        assertEquals(repaidMessage, higher.getMessage());
        IllegalStateException voidRejected = assertThrows(IllegalStateException.class, () -> purchaseService.voidPurchase(
                purchase.id(), new VoidPurchaseRequest("Error"), "it-ship"));
        assertEquals(repaidMessage, voidRejected.getMessage());

        UUID repaymentId = repaid.entries().stream()
                .filter(e -> e.type() == CreditorHistoryResponse.EntryType.REPAYMENT).findFirst().orElseThrow().id();
        creditorService.voidRepayment(mami, repaymentId, new VoidDebtRequest("Error"), "it-ship");
        entityManager.flush();
        assertDebt("30.00");

        // Pending again: it can change, and only the difference is new debt (the unique active shipping holds)
        purchaseService.updatePurchase(purchase.id(), edit(current, "35.00", null, mami, "Subió el envío"), "it-ship");
        entityManager.flush();
        assertDebt("35.00");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM purchase_shipping_charges WHERE purchase_id = ? "
                + "AND status = 'ACTIVO'", Integer.class, purchase.id()));

        PurchaseResponse voided = purchaseService.voidPurchase(purchase.id(), new VoidPurchaseRequest("Pedido duplicado"), "it-ship");
        entityManager.flush();
        assertEquals(PurchaseStatus.ANULADO, voided.status());
        assertDebt("0.00");
        assertBalance("1000.00", bisa);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM purchase_shipping_charges WHERE purchase_id = ? "
                + "AND status = 'ACTIVO'", Integer.class, purchase.id()));
        assertEquals(mamiName, voided.shippingCreditorName());
    }

    @Test
    @Transactional
    void aPurchaseHasOneActiveShippingByPerson() {
        setUp();
        UUID purchase = jdbc.queryForObject("INSERT INTO purchases (purchase_number, supplier_id, purchase_date, items_total, "
                + "shipping_cost, total_amount, created_by) VALUES (?, ?, CURRENT_DATE, 10, 5, 15, 'it-ship') RETURNING id",
                UUID.class, "IT-SHIP-" + suffix(), supplier);
        String insert = "INSERT INTO purchase_shipping_charges (purchase_id, creditor_id, amount, charge_date, operation_date, "
                + "status, created_by) VALUES (?, ?, 5, CURRENT_TIMESTAMP, CURRENT_DATE, ?, 'it-ship')";
        jdbc.update(insert, purchase, mami, "ANULADO");
        jdbc.update(insert, purchase, mami, "ACTIVO");
        assertEquals(0, new BigDecimal("5.00").compareTo(creditorRepository.computeDebt(mami)));
        BigDecimal fromList = creditorRepository.computeDebts().stream()
                .filter(row -> mami.equals(row[0])).map(row -> (BigDecimal) row[1]).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("5.00").compareTo(fromList));

        // Last: PostgreSQL aborts the transaction after the violation
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, purchase, mami, "ACTIVO"));
    }

    private String suffixValue;

    private String suffix() {
        return suffixValue;
    }

    private void setUp() {
        suffixValue = UUID.randomUUID().toString().substring(0, 8);
        bisa = jdbc.queryForObject("INSERT INTO financial_accounts (name, current_balance) VALUES (?, 1000) RETURNING id",
                UUID.class, "IT Bisa " + suffix());
        supplier = jdbc.queryForObject("INSERT INTO suppliers (name, code, created_by) VALUES (?, ?, 'it-ship') RETURNING id",
                UUID.class, "IT proveedor " + suffix(), "ITE-" + suffix());
        UUID category = jdbc.queryForObject("INSERT INTO categories (name, color, created_by) VALUES (?, '#000000', 'it-ship') RETURNING id",
                UUID.class, "IT categoría " + suffix());
        product = jdbc.queryForObject(
                "INSERT INTO products (code, name, category_id, supplier_id, buy_price, sell_price, fixed_profit, percentage_profit, sale_unit, created_by) "
                        + "VALUES (?, ?, ?, ?, 10.00, 15.00, 5.00, 50.00, 'UNIT', 'it-ship') RETURNING id",
                UUID.class, "ITE-" + suffix(), "IT producto " + suffix(), category, supplier);
        mamiName = "IT Mami " + suffix();
        mami = creditorService.createCreditor(new CreditorRequest(mamiName, null, null, null), "it-ship").id();
        // The person exists for the rows inserted with JDBC
        entityManager.flush();
    }

    // The purchase as it is, with another shipping and its source
    private PurchaseUpdateRequest edit(PurchaseResponse purchase, String shipping, UUID accountId, UUID creditorId, String reason) {
        List<PurchaseItemRequest> items = purchase.items().stream()
                .map(i -> new PurchaseItemRequest(i.productId(), i.quantity(), i.unitCost(), false, null, i.id()))
                .toList();
        return new PurchaseUpdateRequest(purchase.supplierId(), purchase.purchaseDate(), items, new BigDecimal(shipping),
                accountId, purchase.consignment(), purchase.comment(), purchase.deliveryType(), purchase.deliveryStatus(),
                purchase.deliveryDate(), reason, null, null, creditorId);
    }

    private void assertDebt(String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(creditorRepository.computeDebt(mami)),
                "debt " + creditorRepository.computeDebt(mami));
    }

    private void assertBalance(String expected, UUID account) {
        assertEquals(0, new BigDecimal(expected).compareTo(
                jdbc.queryForObject("SELECT current_balance FROM financial_accounts WHERE id = ?", BigDecimal.class, account)));
    }
}
