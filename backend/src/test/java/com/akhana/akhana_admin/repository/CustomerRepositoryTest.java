package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CustomerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** CON-02 and DEU-05 to DEU-07 against PostgreSQL: V12 schema, credit only on at-cost sales and computed receivables. */
@SpringBootTest
class CustomerRepositoryTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private CustomerCollectionRepository customerCollectionRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @Transactional
    void nameIsUniqueIgnoringCaseExceptDeleted() {
        String name = "IT Neida " + UUID.randomUUID().toString().substring(0, 8);
        UUID first = insertCustomer(name, "ACTIVO");
        jdbc.update("UPDATE customers SET status = 'ELIMINADO' WHERE id = ?", first);
        assertNotNull(insertCustomer(name.toLowerCase(), "ACTIVO"));
        assertTrue(customerRepository.existsByNameIgnoreCaseAndStatusNot(name.toUpperCase(), CustomerStatus.ELIMINADO));

        // Last: PostgreSQL aborts the transaction after the violation
        assertThrows(DataIntegrityViolationException.class, () -> insertCustomer(name.toUpperCase(), "INACTIVO"));
    }

    @Test
    @Transactional
    void salesDefaultToNormalWithoutCredit() {
        UUID session = closedSession();
        UUID sale = jdbc.queryForObject("INSERT INTO sales (sale_number, cash_session_id, payment_method, total_amount, created_by) "
                + "VALUES (?, ?, 'EFECTIVO', 20.00, 'it-repo') RETURNING id", UUID.class, "IT-" + UUID.randomUUID(), session);
        assertEquals("NORMAL", jdbc.queryForObject("SELECT sale_type FROM sales WHERE id = ?", String.class, sale));
        assertEquals(0, BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT credit_amount FROM sales WHERE id = ?", BigDecimal.class, sale)));
    }

    @Test
    @Transactional
    void fullCreditSaleIsAllowed() {
        UUID customer = insertCustomer("IT Crédito " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        assertNotNull(insertSale(closedSession(), "AL_COSTO", "CREDITO", customer, "579.00", "579.00", "COMPLETADA"));
    }

    @Test
    @Transactional
    void creditNeedsAtCostSaleWithCustomer() {
        UUID customer = insertCustomer("IT Normal " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID session = closedSession();
        assertThrows(DataIntegrityViolationException.class,
                () -> insertSale(session, "NORMAL", "EFECTIVO", customer, "20.00", "5.00", "COMPLETADA"));
    }

    @Test
    @Transactional
    void creditCannotExceedTheTotal() {
        UUID customer = insertCustomer("IT Exceso " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID session = closedSession();
        assertThrows(DataIntegrityViolationException.class,
                () -> insertSale(session, "AL_COSTO", "CREDITO", customer, "20.00", "20.01", "COMPLETADA"));
    }

    @Test
    @Transactional
    void balanceIsActiveCreditMinusActiveCollections() {
        UUID neida = insertCustomer("IT Saldo " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID other = insertCustomer("IT Sin saldo " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID session = closedSession();
        UUID account = anyAccount();

        insertSale(session, "AL_COSTO", "CREDITO", neida, "579.00", "579.00", "COMPLETADA");
        insertSale(session, "AL_COSTO", "QR", neida, "120.00", "80.00", "COMPLETADA");
        insertSale(session, "AL_COSTO", "CREDITO", neida, "60.00", "60.00", "ANULADA");
        insertSale(session, "AL_COSTO", "EFECTIVO", neida, "25.00", "0.00", "COMPLETADA");
        insertCollection(neida, account, "500.00", "ACTIVO");
        insertCollection(neida, account, "40.00", "ANULADO");

        assertEquals(0, new BigDecimal("159.00").compareTo(customerRepository.computeBalance(neida)));
        assertEquals(0, BigDecimal.ZERO.compareTo(customerRepository.computeBalance(other)));
        BigDecimal fromList = customerRepository.computeBalances().stream()
                .filter(row -> neida.equals(row[0]))
                .map(row -> (BigDecimal) row[1])
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("159.00").compareTo(fromList));
        assertEquals(4, saleRepository.findSalesByCustomerId(neida).size());
        assertEquals(2, customerCollectionRepository.findByCustomerId(neida).size());
    }

    @Test
    @Transactional
    void collectionIncomeLinksToItsCollection() {
        UUID neida = insertCustomer("IT Vínculo " + UUID.randomUUID().toString().substring(0, 8), "ACTIVO");
        UUID account = anyAccount();
        UUID collection = insertCollection(neida, account, "10.00", "ACTIVO");
        String insertTx = "INSERT INTO financial_transactions (type, source_account_id, amount, transaction_date, description, "
                + "customer_collection_id, created_by) VALUES ('INGRESO_MANUAL', ?, 10.00, CURRENT_TIMESTAMP, 'IT', ?, 'it-repo')";
        jdbc.update(insertTx, account, collection);

        // One income per collection
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insertTx, account, collection));
    }

    private UUID anyAccount() {
        return jdbc.queryForObject("SELECT id FROM financial_accounts WHERE deleted_at IS NULL LIMIT 1", UUID.class);
    }

    // Closed so it never collides with a real open session; rolled back at the end
    private UUID closedSession() {
        return jdbc.queryForObject(
                "INSERT INTO cash_sessions (status, opening_amount, opened_by) VALUES ('CERRADA', 100, 'it-repo') RETURNING id", UUID.class);
    }

    private UUID insertCustomer(String name, String status) {
        return jdbc.queryForObject("INSERT INTO customers (name, status, created_by) VALUES (?, ?, 'it-repo') RETURNING id",
                UUID.class, name, status);
    }

    private UUID insertSale(UUID session, String type, String method, UUID customer, String total, String credit, String status) {
        return jdbc.queryForObject("INSERT INTO sales (sale_number, cash_session_id, sale_type, payment_method, customer_id, "
                        + "total_amount, credit_amount, status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'it-repo') RETURNING id",
                UUID.class, "IT-" + UUID.randomUUID(), session, type, method, customer, new BigDecimal(total),
                new BigDecimal(credit), status);
    }

    private UUID insertCollection(UUID customer, UUID account, String amount, String status) {
        return jdbc.queryForObject("INSERT INTO customer_collections (customer_id, account_id, amount, collection_date, status, created_by) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, 'it-repo') RETURNING id", UUID.class, customer, account, new BigDecimal(amount), status);
    }
}
