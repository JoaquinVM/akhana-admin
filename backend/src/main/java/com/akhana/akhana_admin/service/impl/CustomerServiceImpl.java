package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    static final String CASH_SESSION_REQUIRED = "Los movimientos de la cuenta Caja requieren una caja abierta.";
    static final String SALES_CATEGORY_MISSING = "No existe la categoría Venta; no se puede registrar el cobro.";
    static final String COLLECTION_ACCOUNT = "Los cobros entran a Caja o a la cuenta QR.";
    static final String VOID_REASON_REQUIRED = "El motivo de anulación es obligatorio.";
    static final String ALREADY_VOIDED_COLLECTION = "El cobro ya está anulado.";
    static final String CLOSED_SESSION_COLLECTION = "El cobro se hizo en una caja cerrada; no se puede anular.";
    static final String ALREADY_DELETED = "El cliente ya está eliminado.";
    static final String DELETED_EDIT = "Un cliente eliminado no se puede editar.";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CustomerRepository customerRepository;
    private final CustomerCollectionRepository customerCollectionRepository;
    private final SaleRepository saleRepository;
    private final CashSessionRepository cashSessionRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ExpectedCashCalculator expectedCashCalculator;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomers(CustomerStatus status, String search) {
        String term = search != null && !search.isBlank() ? search.trim() : null;
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (Object[] row : customerRepository.computeBalances()) {
            balances.put((UUID) row[0], scale((BigDecimal) row[1]));
        }
        return customerRepository.search(status, term).stream()
            .map(c -> CustomerResponse.fromEntity(c, balances.getOrDefault(c.getId(), ZERO)))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID id) {
        Customer customer = findCustomer(id);
        return CustomerResponse.fromEntity(customer, balanceOf(customer));
    }

    @Override
    public CustomerResponse createCustomer(CustomerRequest request, String username) {
        String name = request.name().trim();
        if (customerRepository.existsByNameIgnoreCaseAndStatusNot(name, CustomerStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe un cliente con el nombre '" + name + "'.");
        }
        CustomerStatus status = request.status() == CustomerStatus.INACTIVO ? CustomerStatus.INACTIVO : CustomerStatus.ACTIVO;
        Customer saved = customerRepository.save(Customer.builder()
            .name(name)
            .phone(trimToNull(request.phone()))
            .description(trimToNull(request.description()))
            .status(status)
            .createdBy(username)
            .createdAt(Instant.now())
            .build());
        return CustomerResponse.fromEntity(saved, ZERO);
    }

    @Override
    public CustomerResponse updateCustomer(UUID id, CustomerRequest request, String username) {
        Customer customer = lockCustomer(id);
        if (customer.getStatus() == CustomerStatus.ELIMINADO) {
            throw new IllegalStateException(DELETED_EDIT);
        }
        String name = request.name().trim();
        if (customerRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(name, CustomerStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe un cliente con el nombre '" + name + "'.");
        }
        BigDecimal balance = balanceOf(customer);
        if (request.status() == CustomerStatus.INACTIVO && customer.getStatus() != CustomerStatus.INACTIVO) {
            ensureNoBalance(customer, balance);
        }

        customer.setName(name);
        customer.setPhone(trimToNull(request.phone()));
        customer.setDescription(trimToNull(request.description()));
        if (request.status() != null && request.status() != CustomerStatus.ELIMINADO) {
            customer.setStatus(request.status());
        }
        customer.setUpdatedBy(username);
        customer.setUpdatedAt(Instant.now());
        return CustomerResponse.fromEntity(customerRepository.save(customer), balance);
    }

    @Override
    public void deleteCustomer(UUID id, String username) {
        Customer customer = lockCustomer(id);
        if (customer.getStatus() == CustomerStatus.ELIMINADO) {
            throw new IllegalStateException(ALREADY_DELETED);
        }
        ensureNoBalance(customer, balanceOf(customer));
        // Logical delete: the history stays visible (RG-03)
        customer.setStatus(CustomerStatus.ELIMINADO);
        customer.setDeletedBy(username);
        customer.setDeletedAt(Instant.now());
        customerRepository.save(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerHistoryResponse getHistory(UUID id) {
        Customer customer = findCustomer(id);

        // Credit sales raise the receivable and collections lower it; both by server date (T-18).
        // A sale paid at once (credit 0, P-49) is listed as paid and keeps the balance
        record Item(Instant date, Instant createdAt, CustomerHistoryResponse.Entry entry, BigDecimal delta) {}
        List<Item> items = new ArrayList<>();
        for (Sale s : saleRepository.findSalesByCustomerId(id)) {
            boolean voided = s.getStatus() == SaleStatus.ANULADA;
            items.add(new Item(s.getCreatedAt(), s.getCreatedAt(), new CustomerHistoryResponse.Entry(
                CustomerHistoryResponse.EntryType.SALE, s.getId(), s.getCreatedAt(), s.getCreditAmount(),
                s.getId(), s.getSaleNumber(), s.getTotalAmount(), null, null, null,
                voided, s.getVoidedBy(), s.getVoidedAt(), s.getVoidReason(), null),
                voided ? ZERO : s.getCreditAmount()));
        }
        for (CustomerCollection k : customerCollectionRepository.findByCustomerId(id)) {
            boolean voided = k.getStatus() == CustomerCollectionStatus.ANULADO;
            items.add(new Item(k.getCollectionDate(), k.getCreatedAt(), new CustomerHistoryResponse.Entry(
                CustomerHistoryResponse.EntryType.COLLECTION, k.getId(), k.getCollectionDate(), k.getAmount(),
                null, null, null, k.getAccount().getId(), k.getAccount().getName(), k.getComment(),
                voided, k.getVoidedBy(), k.getVoidedAt(), k.getVoidReason(), null),
                voided ? ZERO : k.getAmount().negate()));
        }
        items.sort(Comparator.comparing(Item::date).thenComparing(Item::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())));

        BigDecimal balance = ZERO;
        List<CustomerHistoryResponse.Entry> entries = new ArrayList<>();
        for (Item item : items) {
            balance = balance.add(item.delta());
            CustomerHistoryResponse.Entry e = item.entry();
            entries.add(new CustomerHistoryResponse.Entry(e.type(), e.id(), e.date(), e.amount(), e.saleId(),
                e.saleNumber(), e.saleTotal(), e.accountId(), e.accountName(), e.comment(), e.voided(),
                e.voidedBy(), e.voidedAt(), e.voidReason(), balance));
        }
        return new CustomerHistoryResponse(CustomerResponse.fromEntity(customer, balance), entries);
    }

    @Override
    public CustomerHistoryResponse registerCollection(UUID customerId, CustomerCollectionRequest request, String username) {
        BigDecimal amount = FinancialTransactionServiceImpl.normalizeAmount(request.amount());

        // Lock order for every money operation, as in sales: cash session, then customer, then account (T-14).
        // An inactive customer can still pay.
        CashSession session = null;
        if (financialAccountRepository.existsByIdAndIsCashAccountTrue(request.accountId())) {
            session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CASH_SESSION_REQUIRED));
        }
        Customer customer = lockCustomer(customerId);
        BigDecimal balance = balanceOf(customer);
        if (amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException("El cobro supera el saldo por cobrar de " + customer.getName()
                + " (Bs " + balance.toPlainString() + ").");
        }
        FinancialCategory category = financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()
            .orElseThrow(() -> new IllegalStateException(SALES_CATEGORY_MISSING));

        FinancialAccount account = financialAccountRepository.findActiveByIdForUpdate(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + request.accountId()));
        // Collections are sales income: only Caja or the QR account take them (DEU-06, FIN-03)
        if (!Boolean.TRUE.equals(account.getIsCashAccount()) && !Boolean.TRUE.equals(account.getIsQrAccount())) {
            throw new IllegalArgumentException(COLLECTION_ACCOUNT);
        }

        Instant now = Instant.now();
        String comment = trimToNull(request.comment());
        // Saved before its income, which points to it
        CustomerCollection collection = customerCollectionRepository.save(CustomerCollection.builder()
            .customer(customer)
            .account(account)
            .amount(amount)
            .collectionDate(now)
            .comment(comment)
            .status(CustomerCollectionStatus.ACTIVO)
            .createdBy(username)
            .createdAt(now)
            .build());

        // Deferred payment of an at-cost sale: a Venta income (P-34); into Caja it is a cash income (CAJ-03)
        account.setCurrentBalance(account.getCurrentBalance().add(amount));
        financialAccountRepository.save(account);
        financialTransactionRepository.save(FinancialTransaction.builder()
            .type(FinancialTransactionType.INGRESO_MANUAL)
            .sourceAccount(account)
            .category(category)
            .amount(amount)
            .transactionDate(now)
            .description("Cobro a " + customer.getName())
            .notes(comment)
            .status(FinancialTransactionStatus.ACTIVO)
            .customerCollectionId(collection.getId())
            .cashSessionId(session != null ? session.getId() : null)
            .createdBy(username)
            .createdAt(now)
            .build());

        if (session != null) {
            // Flushes first so the repository sums see the new income
            financialTransactionRepository.flush();
            expectedCashCalculator.refresh(session);
            cashSessionRepository.save(session);
        }
        return getHistory(customerId);
    }

    @Override
    public CustomerHistoryResponse voidCollection(UUID customerId, UUID collectionId, VoidDebtRequest request, String username) {
        if (request == null || request.voidReason() == null || request.voidReason().isBlank()) {
            throw new IllegalArgumentException(VOID_REASON_REQUIRED);
        }
        String voidReason = request.voidReason().trim();

        // The movement is read first only to know its session, which never changes
        FinancialTransaction tx = financialTransactionRepository.findByCustomerCollectionId(collectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Cobro no encontrado con ID: " + collectionId));

        // Lock order as in sales: cash session, then customer, then account (T-14). A Caja movement needs its own
        // session still open (CAJ-06, T-16)
        CashSession session = null;
        if (tx.getCashSessionId() != null) {
            session = cashSessionRepository.findByIdForUpdate(tx.getCashSessionId())
                .filter(s -> s.getStatus() == CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CLOSED_SESSION_COLLECTION));
        }
        lockCustomer(customerId);
        // Read after the locks, so a second void sees it already voided
        CustomerCollection collection = customerCollectionRepository.findById(collectionId)
            .filter(k -> k.getCustomer().getId().equals(customerId))
            .orElseThrow(() -> new ResourceNotFoundException("Cobro no encontrado con ID: " + collectionId));
        if (collection.getStatus() == CustomerCollectionStatus.ANULADO) {
            throw new IllegalStateException(ALREADY_VOIDED_COLLECTION);
        }
        UUID accountId = tx.getSourceAccount().getId();
        FinancialAccount account = financialAccountRepository.findAllByIdForUpdate(Set.of(accountId)).stream()
            .findFirst()
            .orElse(tx.getSourceAccount());

        Instant now = Instant.now();
        account.setCurrentBalance(account.getCurrentBalance().subtract(tx.getAmount()));
        financialAccountRepository.save(account);
        tx.setStatus(FinancialTransactionStatus.ANULADO);
        tx.setVoidedBy(username);
        tx.setVoidedAt(now);
        tx.setVoidReason(voidReason);
        financialTransactionRepository.save(tx);

        collection.setStatus(CustomerCollectionStatus.ANULADO);
        collection.setVoidedBy(username);
        collection.setVoidedAt(now);
        collection.setVoidReason(voidReason);
        customerCollectionRepository.save(collection);

        if (session != null) {
            // Flushes first so the repository sums see the voided movement
            financialTransactionRepository.flush();
            expectedCashCalculator.refresh(session);
            cashSessionRepository.save(session);
        }
        return getHistory(customerId);
    }

    private Customer findCustomer(UUID id) {
        return customerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
    }

    private Customer lockCustomer(UUID id) {
        return customerRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
    }

    private BigDecimal balanceOf(Customer customer) {
        return scale(customerRepository.computeBalance(customer.getId()));
    }

    // DEU-05: a customer with a receivable cannot be deactivated or deleted
    private static void ensureNoBalance(Customer customer, BigDecimal balance) {
        if (balance.signum() > 0) {
            throw new IllegalStateException(customer.getName() + " tiene un saldo por cobrar de Bs "
                + balance.toPlainString() + ".");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : ZERO;
    }

    private static String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
