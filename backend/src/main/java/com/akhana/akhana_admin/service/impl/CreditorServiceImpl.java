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
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditorServiceImpl implements CreditorService {

    static final String CASH_SESSION_REQUIRED = "Los movimientos de la cuenta Caja requieren una caja abierta.";
    static final String PAYMENT_CATEGORY_MISSING = "No existe la categoría Pedido; no se puede registrar la devolución.";
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

        // Payments raise the debt and repayments lower it; both by server date (T-18)
        record Item(Instant date, Instant createdAt, CreditorHistoryResponse.Entry entry, BigDecimal delta) {}
        List<Item> items = new ArrayList<>();
        for (PurchasePayment p : purchasePaymentRepository.findByCreditorId(id)) {
            boolean voided = p.getStatus() == PurchasePaymentRecordStatus.ANULADO;
            items.add(new Item(p.getPaymentDate(), p.getCreatedAt(), new CreditorHistoryResponse.Entry(
                CreditorHistoryResponse.EntryType.PAYMENT, p.getId(), p.getPaymentDate(), p.getAmount(),
                p.getPurchase().getId(), p.getPurchase().getPurchaseNumber(), p.getPurchase().getSupplier().getName(),
                null, null, null, voided, p.getVoidedBy(), p.getVoidedAt(), p.getVoidReason(), null),
                voided ? ZERO : p.getAmount()));
        }
        for (CreditorRepayment r : creditorRepaymentRepository.findByCreditorId(id)) {
            boolean voided = r.getStatus() == CreditorRepaymentStatus.ANULADO;
            items.add(new Item(r.getRepaymentDate(), r.getCreatedAt(), new CreditorHistoryResponse.Entry(
                CreditorHistoryResponse.EntryType.REPAYMENT, r.getId(), r.getRepaymentDate(), r.getAmount(),
                null, null, null, r.getAccount().getId(), r.getAccount().getName(), r.getComment(),
                voided, r.getVoidedBy(), r.getVoidedAt(), r.getVoidReason(), null),
                voided ? ZERO : r.getAmount().negate()));
        }
        items.sort(Comparator.comparing(Item::date).thenComparing(Item::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())));

        BigDecimal balance = ZERO;
        List<CreditorHistoryResponse.Entry> entries = new ArrayList<>();
        for (Item item : items) {
            balance = balance.add(item.delta());
            CreditorHistoryResponse.Entry e = item.entry();
            entries.add(new CreditorHistoryResponse.Entry(e.type(), e.id(), e.date(), e.amount(), e.purchaseId(),
                e.purchaseNumber(), e.supplierName(), e.accountId(), e.accountName(), e.comment(), e.voided(),
                e.voidedBy(), e.voidedAt(), e.voidReason(), balance));
        }
        return new CreditorHistoryResponse(CreditorResponse.fromEntity(creditor, balance), entries);
    }

    @Override
    public CreditorHistoryResponse registerRepayment(UUID creditorId, CreditorRepaymentRequest request, String username) {
        BigDecimal amount = FinancialTransactionServiceImpl.normalizeAmount(request.amount());

        // Lock order: person, then cash session, then account (T-14). An inactive person can still be repaid.
        Creditor creditor = lockCreditor(creditorId);
        BigDecimal debt = debtOf(creditor);
        if (amount.compareTo(debt) > 0) {
            throw new IllegalArgumentException("La devolución supera la deuda con " + creditor.getName()
                + " (Bs " + debt.toPlainString() + ").");
        }
        FinancialCategory category = financialCategoryRepository
            .findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_PAYMENT)
            .orElseThrow(() -> new IllegalStateException(PAYMENT_CATEGORY_MISSING));

        CashSession session = null;
        if (financialAccountRepository.existsByIdAndIsCashAccountTrue(request.accountId())) {
            session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CASH_SESSION_REQUIRED));
        }
        FinancialAccount account = financialAccountRepository.findActiveByIdForUpdate(request.accountId())
            .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada con ID: " + request.accountId()));

        Instant now = Instant.now();
        String comment = trimToNull(request.comment());
        // Saved before its expense, which points to it
        CreditorRepayment repayment = creditorRepaymentRepository.save(CreditorRepayment.builder()
            .creditor(creditor)
            .account(account)
            .amount(amount)
            .repaymentDate(now)
            .comment(comment)
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
            .description("Devolución a " + creditor.getName())
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

        // Lock order: person, then cash session, then account (T-14)
        lockCreditor(creditorId);
        CreditorRepayment repayment = creditorRepaymentRepository.findById(repaymentId)
            .filter(r -> r.getCreditor().getId().equals(creditorId))
            .orElseThrow(() -> new ResourceNotFoundException("Devolución no encontrada con ID: " + repaymentId));
        if (repayment.getStatus() == CreditorRepaymentStatus.ANULADO) {
            throw new IllegalStateException(ALREADY_VOIDED_REPAYMENT);
        }
        FinancialTransaction tx = financialTransactionRepository.findByCreditorRepaymentId(repaymentId)
            .orElseThrow(() -> new IllegalStateException("No se encontró el movimiento de la devolución."));

        // A Caja movement needs its own session still open (CAJ-06, T-16)
        CashSession session = null;
        if (tx.getCashSessionId() != null) {
            session = cashSessionRepository.findByIdForUpdate(tx.getCashSessionId())
                .filter(s -> s.getStatus() == CashSessionStatus.ABIERTA)
                .orElseThrow(() -> new IllegalStateException(CLOSED_SESSION_REPAYMENT));
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
            .map(a -> new PaymentAccountResponse(a.getId(), a.getName(), a.getColor(), Boolean.TRUE.equals(a.getIsCashAccount())))
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
            throw new IllegalStateException(creditor.getName() + " tiene una deuda de Bs " + debt.toPlainString()
                + "; devuélvala antes de darla de baja.");
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : ZERO;
    }

    private static String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
