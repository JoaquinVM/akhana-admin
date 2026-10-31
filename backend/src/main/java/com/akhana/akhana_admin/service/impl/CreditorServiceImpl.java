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
    static final String THIRD_PARTY_OVER_PENDING = "El Pago a tercero supera lo pendiente de las ventas de %s (%s); registre el resto con el concepto Pedido.";
    static final String THIRD_PARTY_CATEGORY_MISSING = "No existe la categoría Pago a tercero; no se puede registrar la devolución.";
    static final String VOID_REASON_REQUIRED = "El motivo de anulación es obligatorio.";
    static final String ALREADY_VOIDED_REPAYMENT = "La devolución ya está anulada.";
    static final String CLOSED_SESSION_REPAYMENT = "La devolución se hizo en una caja cerrada; no se puede anular.";
    static final String ALREADY_DELETED = "La persona ya está eliminada.";
    static final String DELETED_EDIT = "Una persona eliminada no se puede editar.";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CreditorRepository creditorRepository;
    private final CreditorRepaymentRepository creditorRepaymentRepository;
    private final PurchasePaymentRepository purchasePaymentRepository;
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

        // Payments raise the debt and repayments lower it; by day of operation (CAJ-11), then server date (T-18)
        record Item(LocalDate day, Instant date, Instant createdAt, CreditorHistoryResponse.Entry entry, BigDecimal delta) {}
        List<Item> items = new ArrayList<>();
        for (PurchasePayment p : purchasePaymentRepository.findByCreditorId(id)) {
            boolean voided = p.getStatus() == PurchasePaymentRecordStatus.ANULADO;
            items.add(new Item(p.getOperationDate(), p.getPaymentDate(), p.getCreatedAt(), new CreditorHistoryResponse.Entry(
                CreditorHistoryResponse.EntryType.PAYMENT, p.getId(), p.getPaymentDate(), p.getOperationDate(), p.getAmount(),
                p.getPurchase().getId(), p.getPurchase().getPurchaseNumber(), p.getPurchase().getSupplier().getName(),
                null, null, null, null, null, null, voided, p.getVoidedBy(), p.getVoidedAt(), p.getVoidReason(), null),
                voided ? ZERO : p.getAmount()));
        }
        // POS-13: one entry per sale with the person's lines, on the day of the sale (CAJ-11)
        Map<UUID, List<SaleItem>> linesBySale = new LinkedHashMap<>();
        for (SaleItem line : saleItemRepository.findThirdPartyLinesByCreditorId(id)) {
            linesBySale.computeIfAbsent(line.getSale().getId(), s -> new ArrayList<>()).add(line);
        }
        for (List<SaleItem> lines : linesBySale.values()) {
            Sale s = lines.getFirst().getSale();
            BigDecimal amount = lines.stream().map(SaleItem::getSubtotal).reduce(ZERO, BigDecimal::add);
            boolean voided = s.getStatus() == SaleStatus.ANULADA;
            items.add(new Item(s.getOperationDate(), s.getCreatedAt(), s.getCreatedAt(), new CreditorHistoryResponse.Entry(
                CreditorHistoryResponse.EntryType.SALE, s.getId(), s.getCreatedAt(), s.getOperationDate(), amount,
                null, null, null,
                s.getId(), s.getSaleNumber(), null, null, null, null, voided, s.getVoidedBy(), s.getVoidedAt(),
                s.getVoidReason(), null),
                voided ? ZERO : amount));
        }
        for (CreditorRepayment r : creditorRepaymentRepository.findByCreditorId(id)) {
            boolean voided = r.getStatus() == CreditorRepaymentStatus.ANULADO;
            items.add(new Item(r.getOperationDate(), r.getRepaymentDate(), r.getCreatedAt(), new CreditorHistoryResponse.Entry(
                CreditorHistoryResponse.EntryType.REPAYMENT, r.getId(), r.getRepaymentDate(), r.getOperationDate(), r.getAmount(),
                null, null, null, null, null, r.getAccount().getId(), r.getAccount().getName(), r.getComment(),
                r.getConcept(), voided, r.getVoidedBy(), r.getVoidedAt(), r.getVoidReason(), null),
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
                e.comment(), e.concept(), e.voided(), e.voidedBy(), e.voidedAt(), e.voidReason(), balance));
        }
        return new CreditorHistoryResponse(CreditorResponse.fromEntity(creditor, balance), entries);
    }

    @Override
    public CreditorHistoryResponse registerRepayment(UUID creditorId, CreditorRepaymentRequest request, String username) {
        BigDecimal amount = FinancialTransactionServiceImpl.normalizeAmount(request.amount());

        // Lock order for every money operation, as in sales: cash session, then person, then account (T-14).
        // An inactive person can still be repaid.
        CashSession session = null;
        if (financialAccountRepository.existsByIdAndIsCashAccountTrue(request.accountId())) {
            session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CASH_SESSION_REQUIRED));
        }
        Creditor creditor = lockCreditor(creditorId);
        BigDecimal debt = debtOf(creditor);
        if (amount.compareTo(debt) > 0) {
            throw new IllegalArgumentException("La devolución supera la deuda con " + creditor.getName()
                + " (" + StoreFormat.money(debt) + ").");
        }
        // Purchases go back as Pedido (P-34) and third-party sales as Pago a tercero (POS-13)
        CreditorRepaymentConcept concept = request.concept() != null ? request.concept() : CreditorRepaymentConcept.PURCHASE;
        boolean thirdParty = concept == CreditorRepaymentConcept.THIRD_PARTY;
        // Pago a tercero only pays what is pending of the person's sales (P-48); the rest goes back as Pedido
        if (thirdParty) {
            BigDecimal pending = scale(creditorRepository.computeThirdPartyPending(creditor.getId())).max(ZERO);
            if (amount.compareTo(pending) > 0) {
                throw new IllegalArgumentException(String.format(THIRD_PARTY_OVER_PENDING, creditor.getName(),
                    StoreFormat.money(pending)));
            }
        }
        FinancialCategory category = financialCategoryRepository
            .findBySystemKeyAndDeletedAtIsNull(thirdParty
                ? FinancialCategorySystemKey.THIRD_PARTY_PAYMENT : FinancialCategorySystemKey.PURCHASE_PAYMENT)
            .orElseThrow(() -> new IllegalStateException(thirdParty ? THIRD_PARTY_CATEGORY_MISSING : PAYMENT_CATEGORY_MISSING));

        FinancialAccount account = financialAccountRepository.findActiveByIdForUpdate(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + request.accountId()));

        Instant now = Instant.now();
        // CAJ-11: from Caja it counts for the day of the session; from another account, for the day it is recorded
        LocalDate operationDate = session != null ? session.getOperationDate() : StoreFormat.storeDay(now);
        String comment = trimToNull(request.comment());
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

        // Deferred payment of a purchase: a Pedido expense (P-34), like a payment from an account (COM-06)
        account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
        financialAccountRepository.save(account);
        financialTransactionRepository.save(FinancialTransaction.builder()
            .type(FinancialTransactionType.GASTO_MANUAL)
            .sourceAccount(account)
            .category(category)
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
