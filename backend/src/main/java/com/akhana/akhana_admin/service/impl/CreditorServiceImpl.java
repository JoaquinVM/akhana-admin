package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.CreditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditorServiceImpl implements CreditorService {

    static final String CASH_SESSION_REQUIRED = "Los movimientos de la cuenta Caja requieren una caja abierta.";
    static final String PAYMENT_CATEGORY_MISSING = "No existe la categoría Pedido; no se puede registrar la devolución.";
    static final String NO_DEBTS_CHOSEN = "Elija las deudas que se pagan.";
    static final String DEBTS_CHANGED = "Las deudas elegidas cambiaron; vuelva a abrir el detalle.";
    static final String THIRD_PARTY_CATEGORY_MISSING = "No existe la categoría Pago a tercero; no se puede registrar la devolución.";
    static final String VOID_REASON_REQUIRED = "El motivo de anulación es obligatorio.";
    static final String ALREADY_VOIDED_REPAYMENT = "La devolución ya está anulada.";
    static final String CLOSED_SESSION_REPAYMENT = "La devolución se hizo en una caja cerrada; no se puede anular.";
    static final String ALREADY_DELETED = "La persona ya está eliminada.";
    static final String DELETED_EDIT = "Una persona eliminada no se puede editar.";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CreditorRepository creditorRepository;
    private final CreditorRepaymentRepository creditorRepaymentRepository;
    private final CreditorRepaymentItemRepository creditorRepaymentItemRepository;
    private final PurchasePaymentRepository purchasePaymentRepository;
    private final PurchaseShippingChargeRepository purchaseShippingChargeRepository;
    private final CashSessionRepository cashSessionRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ExpectedCashCalculator expectedCashCalculator;
    private final ProductRepository productRepository;
    private final SaleItemRepository saleItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CreditorResponse> getCreditors(CreditorStatus status, String search) {
        String term = search != null && !search.isBlank() ? search.trim() : null;
        Map<UUID, BigDecimal> debts = new HashMap<>();
        for (Object[] row : creditorRepository.computeDebts()) {
            debts.put((UUID) row[0], scale((BigDecimal) row[1]));
        }
        return creditorRepository.search(status, term).stream()
            .map(c -> CreditorResponse.fromEntity(c, debts.getOrDefault(c.getId(), ZERO)))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CreditorResponse getCreditorById(UUID id) {
        return toResponse(findCreditor(id));
    }

    @Override
    public CreditorResponse createCreditor(CreditorRequest request, String username) {
        String name = request.name().trim();
        if (creditorRepository.existsByNameIgnoreCaseAndStatusNot(name, CreditorStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe una persona con el nombre '" + name + "'.");
        }
        CreditorStatus status = request.status() == CreditorStatus.INACTIVO ? CreditorStatus.INACTIVO : CreditorStatus.ACTIVO;
        Creditor saved = creditorRepository.save(Creditor.builder()
            .name(name)
            .phone(trimToNull(request.phone()))
            .description(trimToNull(request.description()))
            .status(status)
            .createdBy(username)
            .createdAt(Instant.now())
            .build());
        return CreditorResponse.fromEntity(saved, ZERO);
    }

    @Override
    public CreditorResponse updateCreditor(UUID id, CreditorRequest request, String username) {
        Creditor creditor = lockCreditor(id);
        if (creditor.getStatus() == CreditorStatus.ELIMINADO) {
            throw new IllegalStateException(DELETED_EDIT);
        }
        String name = request.name().trim();
        if (creditorRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(name, CreditorStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe una persona con el nombre '" + name + "'.");
        }
        BigDecimal debt = debtOf(creditor);
        if (request.status() == CreditorStatus.INACTIVO && creditor.getStatus() != CreditorStatus.INACTIVO) {
            ensureNoDebt(creditor, debt);
            ensureNoThirdPartyProduct(creditor);
        }

        creditor.setName(name);
        creditor.setPhone(trimToNull(request.phone()));
        creditor.setDescription(trimToNull(request.description()));
        if (request.status() != null && request.status() != CreditorStatus.ELIMINADO) {
            creditor.setStatus(request.status());
        }
        creditor.setUpdatedBy(username);
        creditor.setUpdatedAt(Instant.now());
        return CreditorResponse.fromEntity(creditorRepository.save(creditor), debt);
    }

    @Override
    public void deleteCreditor(UUID id, String username) {
        Creditor creditor = lockCreditor(id);
        if (creditor.getStatus() == CreditorStatus.ELIMINADO) {
            throw new IllegalStateException(ALREADY_DELETED);
        }
        ensureNoDebt(creditor, debtOf(creditor));
        ensureNoThirdPartyProduct(creditor);
        // Logical delete: the history stays visible (RG-03)
        creditor.setStatus(CreditorStatus.ELIMINADO);
        creditor.setDeletedBy(username);
        creditor.setDeletedAt(Instant.now());
        creditorRepository.save(creditor);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditorHistoryResponse getHistory(UUID id) {
        Creditor creditor = findCreditor(id);
        List<Debt> debts = debtsOf(id);
        List<CreditorRepaymentItem> rows = creditorRepaymentItemRepository.findByCreditorId(id);
        Map<DebtKey, BigDecimal> paid = paidByDebt(rows);

        // DEU-03 (062): the day each debt was finished paying, and what each repayment paid, oldest debt first
        Map<DebtKey, LocalDate> paidOn = new HashMap<>();
        Map<UUID, List<CreditorRepaymentItem>> rowsByRepayment = new HashMap<>();
        for (CreditorRepaymentItem row : rows) {
            CreditorRepayment r = row.getRepayment();
            rowsByRepayment.computeIfAbsent(r.getId(), k -> new ArrayList<>()).add(row);
            if (r.getStatus() == CreditorRepaymentStatus.ACTIVO) {
                paidOn.merge(keyOf(row), r.getOperationDate(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<DebtKey, Integer> debtOrder = new HashMap<>();
        Map<DebtKey, String> labels = new HashMap<>();
        for (int i = 0; i < debts.size(); i++) {
            debtOrder.put(debts.get(i).key(), i);
            labels.put(debts.get(i).key(), debts.get(i).label());
        }

        // Debts raise the balance and repayments lower it; by day of operation (CAJ-11), then server date (T-18)
        record Item(LocalDate day, Instant date, Instant createdAt, CreditorHistoryResponse.Entry entry, BigDecimal delta) {}
        List<Item> items = new ArrayList<>();
        for (Debt d : debts) {
            BigDecimal pending = d.voided() ? null : d.amount().subtract(paid.getOrDefault(d.key(), ZERO)).max(ZERO);
            LocalDate paidDay = pending != null && pending.signum() == 0 ? paidOn.get(d.key()) : null;
            items.add(new Item(d.day(), d.date(), d.createdAt(), new CreditorHistoryResponse.Entry(
                d.type(), d.id(), d.date(), d.day(), d.amount(), d.purchaseId(), d.purchaseNumber(), d.supplierName(),
                d.saleId(), d.saleNumber(), null, null, null, null, d.voided(), d.voidedBy(), d.voidedAt(),
                d.voidReason(), pending, paidDay, null, null),
                d.voided() ? ZERO : d.amount()));
        }
        for (CreditorRepayment r : creditorRepaymentRepository.findByCreditorId(id)) {
            boolean voided = r.getStatus() == CreditorRepaymentStatus.ANULADO;
            List<String> paidItems = rowsByRepayment.getOrDefault(r.getId(), List.of()).stream()
                .map(this::keyOf)
                .distinct()
                .sorted(Comparator.comparing(k -> debtOrder.getOrDefault(k, Integer.MAX_VALUE)))
                .map(k -> labels.getOrDefault(k, ""))
                .toList();
            items.add(new Item(r.getOperationDate(), r.getRepaymentDate(), r.getCreatedAt(), new CreditorHistoryResponse.Entry(
                CreditorHistoryResponse.EntryType.REPAYMENT, r.getId(), r.getRepaymentDate(), r.getOperationDate(), r.getAmount(),
                null, null, null, null, null, r.getAccount().getId(), r.getAccount().getName(), r.getComment(),
                r.getConcept(), voided, r.getVoidedBy(), r.getVoidedAt(), r.getVoidReason(), null, null, paidItems, null),
                voided ? ZERO : r.getAmount().negate()));
        }
        items.sort(Comparator.comparing(Item::day).thenComparing(Item::date)
            .thenComparing(Item::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())));

        BigDecimal balance = ZERO;
        List<CreditorHistoryResponse.Entry> entries = new ArrayList<>();
        for (Item item : items) {
            balance = balance.add(item.delta());
            CreditorHistoryResponse.Entry e = item.entry();
            entries.add(new CreditorHistoryResponse.Entry(e.type(), e.id(), e.date(), e.operationDate(), e.amount(), e.purchaseId(),
                e.purchaseNumber(), e.supplierName(), e.saleId(), e.saleNumber(), e.accountId(), e.accountName(),
                e.comment(), e.concept(), e.voided(), e.voidedBy(), e.voidedAt(), e.voidReason(), e.pendingAmount(),
                e.paidOn(), e.paidItems(), balance));
        }
        return new CreditorHistoryResponse(CreditorResponse.fromEntity(creditor, balance), entries);
    }

    @Override
    public CreditorHistoryResponse registerRepayment(UUID creditorId, CreditorRepaymentRequest request, String username) {
        BigDecimal shown = FinancialTransactionServiceImpl.normalizeAmount(request.amount());
        List<CreditorRepaymentRequest.Item> refs = request.items() != null ? request.items() : List.of();
        if (refs.isEmpty() || refs.stream().anyMatch(ref -> ref == null || ref.type() == null || ref.id() == null)) {
            throw new IllegalArgumentException(NO_DEBTS_CHOSEN);
        }

        // Lock order for every money operation, as in sales: cash session, then person, then account (T-14).
        // An inactive person can still be repaid.
        CashSession session = null;
        if (financialAccountRepository.existsByIdAndIsCashAccountTrue(request.accountId())) {
            session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CASH_SESSION_REQUIRED));
        }
        Creditor creditor = lockCreditor(creditorId);

        // DEU-03 (062): each ticked debt is paid whole; what it still owes is read after the lock. A debt already
        // paid, voided, of another person or unknown means the screen is stale: nothing is recorded
        List<Debt> debts = debtsOf(creditorId);
        Map<DebtKey, BigDecimal> paid = paidByDebt(creditorRepaymentItemRepository.findByCreditorId(creditorId));
        Set<DebtKey> ticked = new HashSet<>();
        refs.forEach(ref -> ticked.add(new DebtKey(ref.type(), ref.id())));
        Map<CreditorRepaymentConcept, Map<Debt, BigDecimal>> byConcept = new EnumMap<>(CreditorRepaymentConcept.class);
        BigDecimal total = ZERO;
        for (Debt debt : debts) {
            if (!ticked.remove(debt.key())) {
                continue;
            }
            BigDecimal pending = debt.voided() ? ZERO : debt.amount().subtract(paid.getOrDefault(debt.key(), ZERO));
            if (pending.signum() <= 0) {
                throw new IllegalArgumentException(DEBTS_CHANGED);
            }
            // Sales of their products go back as Pago a tercero (POS-13); purchases and shipping as Pedido (P-34)
            CreditorRepaymentConcept concept = debt.type() == CreditorHistoryResponse.EntryType.SALE
                ? CreditorRepaymentConcept.THIRD_PARTY : CreditorRepaymentConcept.PURCHASE;
            byConcept.computeIfAbsent(concept, c -> new LinkedHashMap<>()).put(debt, pending);
            total = total.add(pending);
        }
        if (!ticked.isEmpty() || total.compareTo(shown) != 0) {
            throw new IllegalArgumentException(DEBTS_CHANGED);
        }
        BigDecimal debt = debtOf(creditor);
        if (total.compareTo(debt) > 0) {
            throw new IllegalArgumentException("La devolución supera la deuda con " + creditor.getName()
                + " (" + StoreFormat.money(debt) + ").");
        }
        Map<CreditorRepaymentConcept, FinancialCategory> categories = new EnumMap<>(CreditorRepaymentConcept.class);
        for (CreditorRepaymentConcept concept : byConcept.keySet()) {
            boolean thirdParty = concept == CreditorRepaymentConcept.THIRD_PARTY;
            categories.put(concept, financialCategoryRepository
                .findBySystemKeyAndDeletedAtIsNull(thirdParty
                    ? FinancialCategorySystemKey.THIRD_PARTY_PAYMENT : FinancialCategorySystemKey.PURCHASE_PAYMENT)
                .orElseThrow(() -> new IllegalStateException(thirdParty ? THIRD_PARTY_CATEGORY_MISSING : PAYMENT_CATEGORY_MISSING)));
        }

        FinancialAccount account = financialAccountRepository.findActiveByIdForUpdate(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + request.accountId()));

        Instant now = Instant.now();
        // CAJ-11: from Caja it counts for the day of the session; from another account, for the day it is recorded
        LocalDate operationDate = session != null ? session.getOperationDate() : StoreFormat.storeDay(now);
        String comment = trimToNull(request.comment());
        // Pago a tercero first, then Pedido: one repayment and one expense per concept, all in this transaction
        for (CreditorRepaymentConcept concept : List.of(CreditorRepaymentConcept.THIRD_PARTY, CreditorRepaymentConcept.PURCHASE)) {
            Map<Debt, BigDecimal> chosen = byConcept.get(concept);
            if (chosen == null) {
                continue;
            }
            boolean thirdParty = concept == CreditorRepaymentConcept.THIRD_PARTY;
            BigDecimal amount = chosen.values().stream().reduce(ZERO, BigDecimal::add);
            // Saved before its expense, which points to it
            CreditorRepayment repayment = creditorRepaymentRepository.save(CreditorRepayment.builder()
                .creditor(creditor)
                .account(account)
                .amount(amount)
                .repaymentDate(now)
                .operationDate(operationDate)
                .comment(comment)
                .concept(concept)
                .status(CreditorRepaymentStatus.ACTIVO)
                .createdBy(username)
                .createdAt(now)
                .build());
            chosen.forEach((d, pending) -> creditorRepaymentItemRepository.save(itemFor(repayment, d, pending)));

            // Deferred payment of a purchase: a Pedido expense (P-34), like a payment from an account (COM-06)
            account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
            financialTransactionRepository.save(FinancialTransaction.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccount(account)
                .category(categories.get(concept))
                .amount(amount)
                .transactionDate(now)
                .operationDate(operationDate)
                .description((thirdParty ? "Pago a tercero a " : "Devolución a ") + creditor.getName())
                .notes(comment)
                .status(FinancialTransactionStatus.ACTIVO)
                .creditorRepaymentId(repayment.getId())
                .cashSessionId(session != null ? session.getId() : null)
                .createdBy(username)
                .createdAt(now)
                .build());
        }
        financialAccountRepository.save(account);

        if (session != null) {
            expectedCashCalculator.refresh(session);
            cashSessionRepository.save(session);
        }
        return getHistory(creditorId);
    }

    @Override
    public CreditorHistoryResponse voidRepayment(UUID creditorId, UUID repaymentId, VoidDebtRequest request, String username) {
        if (request == null || request.voidReason() == null || request.voidReason().isBlank()) {
            throw new IllegalArgumentException(VOID_REASON_REQUIRED);
        }
        String voidReason = request.voidReason().trim();

        // The movement is read first only to know its session, which never changes
        FinancialTransaction tx = financialTransactionRepository.findByCreditorRepaymentId(repaymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Devolución no encontrada con ID: " + repaymentId));

        // Lock order as in sales: cash session, then person, then account (T-14). A Caja movement needs its own
        // session still open (CAJ-06, T-16)
        CashSession session = null;
        if (tx.getCashSessionId() != null) {
            session = cashSessionRepository.findByIdForUpdate(tx.getCashSessionId())
                .filter(s -> s.getStatus() == CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CLOSED_SESSION_REPAYMENT));
        }
        lockCreditor(creditorId);
        // Read after the locks, so a second void sees it already voided
        CreditorRepayment repayment = creditorRepaymentRepository.findById(repaymentId)
            .filter(r -> r.getCreditor().getId().equals(creditorId))
            .orElseThrow(() -> new ResourceNotFoundException("Devolución no encontrada con ID: " + repaymentId));
        if (repayment.getStatus() == CreditorRepaymentStatus.ANULADO) {
            throw new IllegalStateException(ALREADY_VOIDED_REPAYMENT);
        }
        UUID accountId = tx.getSourceAccount().getId();
        FinancialAccount account = financialAccountRepository.findAllByIdForUpdate(Set.of(accountId)).stream()
            .findFirst()
            .orElse(tx.getSourceAccount());

        Instant now = Instant.now();
        account.setCurrentBalance(account.getCurrentBalance().add(tx.getAmount()));
        financialAccountRepository.save(account);
        tx.setStatus(FinancialTransactionStatus.ANULADO);
        tx.setVoidedBy(username);
        tx.setVoidedAt(now);
        tx.setVoidReason(voidReason);
        financialTransactionRepository.save(tx);

        repayment.setStatus(CreditorRepaymentStatus.ANULADO);
        repayment.setVoidedBy(username);
        repayment.setVoidedAt(now);
        repayment.setVoidReason(voidReason);
        creditorRepaymentRepository.save(repayment);

        if (session != null) {
            // Flushes first so the repository sums see the voided movement
            financialTransactionRepository.flush();
            expectedCashCalculator.refresh(session);
            cashSessionRepository.save(session);
        }
        return getHistory(creditorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAccountResponse> getAccounts() {
        return financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
            .map(a -> new PaymentAccountResponse(a.getId(), a.getName(), a.getColor(), Boolean.TRUE.equals(a.getIsCashAccount()),
                Boolean.TRUE.equals(a.getIsQrAccount())))
            .toList();
    }

    // A debt of a person, as the history lists it: what they paid for a purchase or its shipping (DEU-02) or a sale
    // with their products (POS-13); a voided one no longer counts
    private record DebtKey(CreditorHistoryResponse.EntryType type, UUID id) {}

    private record Debt(CreditorHistoryResponse.EntryType type, UUID id, LocalDate day, Instant date, Instant createdAt,
                        BigDecimal amount, boolean voided, UUID purchaseId, String purchaseNumber, String supplierName,
                        UUID saleId, String saleNumber, String voidedBy, Instant voidedAt, String voidReason,
                        // The PurchasePayment, PurchaseShippingCharge or Sale itself
                        Object source) {
        DebtKey key() {
            return new DebtKey(type, id);
        }

        String label() {
            return switch (type) {
                case SHIPPING -> "Envío " + purchaseNumber;
                case SALE -> saleNumber;
                default -> purchaseNumber;
            };
        }
    }

    // The person's debts, oldest first: by day of operation (CAJ-11), then server date (T-18)
    private List<Debt> debtsOf(UUID creditorId) {
        List<Debt> debts = new ArrayList<>();
        for (PurchasePayment p : purchasePaymentRepository.findByCreditorId(creditorId)) {
            debts.add(new Debt(CreditorHistoryResponse.EntryType.PAYMENT, p.getId(), p.getOperationDate(), p.getPaymentDate(),
                p.getCreatedAt(), p.getAmount(), p.getStatus() == PurchasePaymentRecordStatus.ANULADO,
                p.getPurchase().getId(), p.getPurchase().getPurchaseNumber(), p.getPurchase().getSupplier().getName(),
                null, null, p.getVoidedBy(), p.getVoidedAt(), p.getVoidReason(), p));
        }
        // DEU-02: the shipping of a purchase paid by the person
        for (PurchaseShippingCharge c : purchaseShippingChargeRepository.findByCreditorId(creditorId)) {
            debts.add(new Debt(CreditorHistoryResponse.EntryType.SHIPPING, c.getId(), c.getOperationDate(), c.getChargeDate(),
                c.getCreatedAt(), c.getAmount(), c.getStatus() == PurchasePaymentRecordStatus.ANULADO,
                c.getPurchase().getId(), c.getPurchase().getPurchaseNumber(), c.getPurchase().getSupplier().getName(),
                null, null, c.getVoidedBy(), c.getVoidedAt(), c.getVoidReason(), c));
        }
        // POS-13: one debt per sale with the person's lines, on the day of the sale (CAJ-11)
        Map<UUID, List<SaleItem>> linesBySale = new LinkedHashMap<>();
        for (SaleItem line : saleItemRepository.findThirdPartyLinesByCreditorId(creditorId)) {
            linesBySale.computeIfAbsent(line.getSale().getId(), s -> new ArrayList<>()).add(line);
        }
        for (List<SaleItem> lines : linesBySale.values()) {
            Sale s = lines.getFirst().getSale();
            BigDecimal amount = lines.stream().map(SaleItem::getSubtotal).reduce(ZERO, BigDecimal::add);
            debts.add(new Debt(CreditorHistoryResponse.EntryType.SALE, s.getId(), s.getOperationDate(), s.getCreatedAt(),
                s.getCreatedAt(), amount, s.getStatus() == SaleStatus.ANULADA, null, null, null,
                s.getId(), s.getSaleNumber(), s.getVoidedBy(), s.getVoidedAt(), s.getVoidReason(), s));
        }
        debts.sort(Comparator.comparing(Debt::day).thenComparing(Debt::date)
            .thenComparing(Debt::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        return debts;
    }

    // DEU-03 (062): what the active repayments paid of each debt
    private Map<DebtKey, BigDecimal> paidByDebt(List<CreditorRepaymentItem> rows) {
        Map<DebtKey, BigDecimal> paid = new HashMap<>();
        for (CreditorRepaymentItem row : rows) {
            if (row.getRepayment().getStatus() == CreditorRepaymentStatus.ACTIVO) {
                paid.merge(keyOf(row), row.getAmount(), BigDecimal::add);
            }
        }
        return paid;
    }

    private DebtKey keyOf(CreditorRepaymentItem row) {
        CreditorHistoryResponse.EntryType type = row.getPurchasePayment() != null ? CreditorHistoryResponse.EntryType.PAYMENT
            : row.getShippingCharge() != null ? CreditorHistoryResponse.EntryType.SHIPPING
            : CreditorHistoryResponse.EntryType.SALE;
        return new DebtKey(type, row.debtId());
    }

    private static CreditorRepaymentItem itemFor(CreditorRepayment repayment, Debt debt, BigDecimal amount) {
        CreditorRepaymentItem.CreditorRepaymentItemBuilder item = CreditorRepaymentItem.builder()
            .repayment(repayment)
            .amount(amount);
        switch (debt.source()) {
            case PurchasePayment p -> item.purchasePayment(p);
            case PurchaseShippingCharge c -> item.shippingCharge(c);
            case Sale s -> item.sale(s);
            default -> throw new IllegalStateException("Deuda desconocida: " + debt.id());
        }
        return item.build();
    }

    private Creditor findCreditor(UUID id) {
        return creditorRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Persona no encontrada con ID: " + id));
    }

    private Creditor lockCreditor(UUID id) {
        return creditorRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Persona no encontrada con ID: " + id));
    }

    private CreditorResponse toResponse(Creditor creditor) {
        return CreditorResponse.fromEntity(creditor, debtOf(creditor));
    }

    private BigDecimal debtOf(Creditor creditor) {
        return scale(creditorRepository.computeDebt(creditor.getId()));
    }

    // DEU-01: a person with a debt cannot be deactivated or deleted
    private static void ensureNoDebt(Creditor creditor, BigDecimal debt) {
        if (debt.signum() > 0) {
            throw new IllegalStateException(creditor.getName() + " tiene una deuda de " + StoreFormat.money(debt)
                + "; devuélvala antes de darla de baja.");
        }
    }

    // POS-13: the owner of a third-party product stays active while the product exists
    private void ensureNoThirdPartyProduct(Creditor creditor) {
        productRepository.findFirstByOwnerCreditorIdAndStatusNot(creditor.getId(), ProductStatus.ELIMINADO)
            .ifPresent(product -> {
                throw new IllegalStateException(creditor.getName() + " es dueña de '" + product.getName()
                    + "'; dé de baja el producto primero.");
            });
    }

    private static BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : ZERO;
    }

    private static String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
