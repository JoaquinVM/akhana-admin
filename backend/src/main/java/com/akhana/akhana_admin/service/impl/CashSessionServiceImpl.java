package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashDenominationCutRepository;
import com.akhana.akhana_admin.repository.CashNoAttentionDayRepository;
import com.akhana.akhana_admin.repository.CashSessionReopeningRepository;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.CashSessionService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class CashSessionServiceImpl implements CashSessionService {

    private final CashSessionRepository cashSessionRepository;
    private final SaleRepository saleRepository;
    private final ExpectedCashCalculator expectedCashCalculator;
    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialCategoryRepository financialCategoryRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final EntityManager entityManager;
    private final CashNoAttentionDayRepository cashNoAttentionDayRepository;
    private final CashSessionReopeningRepository cashSessionReopeningRepository;
    private final CashDenominationCutRepository cashDenominationCutRepository;

    private static final String ALREADY_OPEN_MESSAGE = "Ya existe una caja abierta en el sistema. Debe cerrarse antes de iniciar una nueva.";
    static final String CASH_ACCOUNT_NOT_FOUND = "No se encontró la cuenta Caja en el sistema.";
    static final String OPENING_COMMENT_REQUIRED = "El monto inicial es distinto al cierre de la caja anterior (%s). Indique el motivo en el comentario de apertura.";
    static final String FIRST_OPENING_COMMENT_REQUIRED = "El monto inicial es distinto al saldo de la cuenta Caja (%s). Indique el motivo en el comentario de apertura.";
    static final String ADJUSTMENT_CATEGORY_MISSING = "Falta la categoría de sistema Ajuste para registrar la diferencia de caja.";
    static final String CUTS_MISMATCH = "Los cortes suman %s y el monto contado es %s; corrija el conteo o deje los cortes vacíos.";
    static final String CUT_DENOMINATION_INVALID = "El corte de %s no existe; use Bs 200, 100, 50, 20, 10, 5, 2, 1, 0,50, 0,20 o 0,10.";
    static final String CUT_DENOMINATION_REPEATED = "El corte de %s está repetido.";
    static final String FUTURE_DAY = "El día de la caja no puede ser futuro.";
    static final String DAY_NOT_AFTER_LAST = "El día debe ser posterior al de la última caja (#%d, %s).";
    static final String DAY_TAKEN = "Ya existe una caja de ese día.";
    static final String PAST_DAY_COMMENT_REQUIRED = "Elegir un día anterior pide un comentario.";
    static final String MISSING_DAYS = "Falta resolver: %s.";
    static final String DAYS_NOT_MISSING = "Esos días ya no faltan; vuelve a abrir el formulario.";
    static final String NO_ATTENTION_COMMENT_REQUIRED = "Confirmar los días sin atención pide un comentario.";
    static final String REOPEN_REASON_REQUIRED = "El motivo es obligatorio.";
    static final String REOPEN_ONLY_LAST = "Solo se puede reabrir la última caja.";
    static final String REOPEN_WITH_OPEN_SESSION = "Ya hay una caja abierta; ciérrala antes de reabrir otra.";
    static final String REOPEN_HISTORICAL = "Una caja histórica no se reabre.";
    static final String REOPEN_ADJUSTMENT_MISMATCH = "El ajuste del cierre de la caja #%d no coincide con su diferencia; no se puede reabrir.";
    private static final int VOID_REASON_MAX = 500;
    private static final String ONE_SESSION_PER_DAY_INDEX = "uq_cash_sessions_operation_date";
    // CAJ-04: the bills and coins of the count
    static final List<BigDecimal> DENOMINATIONS = Stream.of("200", "100", "50", "20", "10", "5", "2", "1", "0.50", "0.20", "0.10")
        .map(value -> new BigDecimal(value).setScale(2, RoundingMode.HALF_UP))
        .toList();

    @Override
    public CashSessionSummaryResponse openSession(OpenCashRequest request, String username) {
        // CAJ-12, CAJ-13: nothing read below about the sessions can change until this transaction ends (T-14)
        cashSessionRepository.lockCashOpening();
        if (cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)) {
            throw new DuplicateResourceException(ALREADY_OPEN_MESSAGE);
        }

        Instant openedAt = Instant.now();
        LocalDate today = StoreFormat.storeDay(openedAt);
        LocalDate day = request.operationDate() != null ? request.operationDate() : today;
        if (day.isAfter(today)) {
            throw new IllegalArgumentException(FUTURE_DAY);
        }
        // A day marked without attention is also rejected here: every mark is before the session that confirmed it
        Optional<CashSession> last = cashSessionRepository.findFirstByOrderByOperationDateDescSessionNumberDesc();
        if (last.isPresent() && !day.isAfter(last.get().getOperationDate())) {
            throw new IllegalArgumentException(String.format(DAY_NOT_AFTER_LAST,
                last.get().getSessionNumber(), StoreFormat.dayMonth(last.get().getOperationDate())));
        }

        List<LocalDate> missingDays = requireConfirmedMissingDays(last, day, request.noAttentionDays());
        String noAttentionComment = trimToNull(request.noAttentionComment());
        if (!missingDays.isEmpty() && noAttentionComment == null) {
            throw new IllegalArgumentException(NO_ATTENTION_COMMENT_REQUIRED);
        }

        BigDecimal openingAmount = request.openingAmount().setScale(2, RoundingMode.HALF_UP);
        String openingComment = trimToNull(request.openingComment());
        if (day.isBefore(today) && openingComment == null) {
            throw new IllegalArgumentException(PAST_DAY_COMMENT_REQUIRED);
        }

        // CAJ-10: an opening amount that differs from the proposal (the previous closing, or the Caja balance for
        // the first session) must be explained. The same comment explains a previous day
        AmountProposal proposal = amountProposal();
        if (openingAmount.compareTo(proposal.amount()) != 0 && openingComment == null) {
            throw new IllegalArgumentException(String.format(
                proposal.sessionNumber() != null ? OPENING_COMMENT_REQUIRED : FIRST_OPENING_COMMENT_REQUIRED,
                StoreFormat.money(proposal.amount())));
        }

        CashSession session = CashSession.builder()
            .status(CashSessionStatus.ABIERTA)
            .openingAmount(openingAmount)
            .openingComment(openingComment)
            .openedBy(username)
            .openedAt(openedAt)
            // CAJ-11, CAJ-12: the chosen day; openedAt keeps the real date and time
            .operationDate(day)
            .expectedCash(openingAmount)
            .totalSalesCash(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .totalSalesQr(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .totalSales(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .build();

        // The lock above already orders the openings; the unique indexes are the last defense of the database
        CashSession saved;
        try {
            saved = cashSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(isDayTaken(e) ? DAY_TAKEN : ALREADY_OPEN_MESSAGE);
        }
        // The session number comes from the database
        entityManager.refresh(saved);
        // CAJ-13: same transaction as the opening, so a failed opening leaves no mark
        cashNoAttentionDayRepository.saveAll(missingDays.stream()
            .map(date -> CashNoAttentionDay.builder()
                .noAttentionDate(date)
                .comment(noAttentionComment)
                .cashSessionId(saved.getId())
                .markedBy(username)
                .markedAt(openedAt)
                .build())
            .toList());
        recordOpeningAdjustment(saved, username);
        return mapToSummary(saved, 0);
    }

    // CAJ-13: the rule looks from the last session on and asks nothing while no session was opened in the system. The
    // client must send exactly the missing days, so nobody marks a day they did not see
    private List<LocalDate> requireConfirmedMissingDays(Optional<CashSession> last, LocalDate day, List<LocalDate> confirmed) {
        List<LocalDate> missing = missingDays(last, day);
        List<LocalDate> sent = confirmed == null ? List.of() : confirmed.stream().distinct().sorted().toList();
        if (!sent.equals(missing)) {
            throw new IllegalArgumentException(missing.isEmpty() ? DAYS_NOT_MISSING : String.format(MISSING_DAYS,
                missing.stream().map(StoreFormat::dayMonth).collect(Collectors.joining(", "))));
        }
        return missing;
    }

    private List<LocalDate> missingDays(Optional<CashSession> last, LocalDate day) {
        if (last.isEmpty() || !cashSessionRepository.existsByHistoricalImportIdIsNull()) {
            return List.of();
        }
        LocalDate lastDay = last.get().getOperationDate();
        Set<LocalDate> marked = day.isAfter(lastDay)
            ? cashNoAttentionDayRepository.findByNoAttentionDateBetweenOrderByNoAttentionDateAsc(lastDay, day).stream()
                .map(CashNoAttentionDay::getNoAttentionDate)
                .collect(Collectors.toSet())
            : Set.of();
        return CashDaySequence.missingDays(lastDay, day, marked);
    }

    private static boolean isDayTaken(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        return cause != null && cause.contains(ONE_SESSION_PER_DAY_INDEX);
    }

    private static String trimToNull(String text) {
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public CashOpeningProposalResponse getOpeningProposal() {
        AmountProposal amount = amountProposal();
        LocalDate today = StoreFormat.today();
        Optional<CashSession> last = cashSessionRepository.findFirstByOrderByOperationDateDescSessionNumberDesc();
        return new CashOpeningProposalResponse(
            amount.amount(),
            amount.sessionNumber(),
            today,
            last.map(session -> session.getOperationDate().plusDays(1)).orElse(null),
            last.map(CashSession::getSessionNumber).orElse(null),
            last.map(CashSession::getOperationDate).orElse(null),
            last.filter(session -> !session.isHistorical() && session.getStatus() == CashSessionStatus.CERRADA
                    && session.getOperationDate().equals(today))
                .map(CashSession::getSessionNumber)
                .orElse(null),
            missingDays(last, today));
    }

    private record AmountProposal(BigDecimal amount, Long sessionNumber) {
    }

    private AmountProposal amountProposal() {
        return cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA)
            .map(previous -> new AmountProposal(
                previous.getClosingAmount().setScale(2, RoundingMode.HALF_UP), previous.getSessionNumber()))
            // First session: the balance of Caja, which holds its cutover balance (FIN-01)
            .orElseGet(() -> new AmountProposal(
                financialAccountRepository.findByIsCashAccountTrue()
                    .map(cash -> cash.getCurrentBalance().setScale(2, RoundingMode.HALF_UP))
                    .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)),
                null));
    }

    // CAJ-14: back to ABIERTA with its number, day, opening amount, sales and movements. The closing adjustment is
    // voided and Caja goes back to what it was before the closing, even below zero (it only returns to that balance).
    // Lock order T-14: opening lock, adjustment, session, account.
    @Override
    public CashSessionSummaryResponse reopenSession(UUID id, ReopenCashRequest request, String username) {
        String reason = request != null ? trimToNull(request.reason()) : null;
        if (reason == null) {
            throw new IllegalArgumentException(REOPEN_REASON_REQUIRED);
        }
        cashSessionRepository.lockCashOpening();
        CashSession found = cashSessionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja no encontrada con ID: " + id));
        if (found.isHistorical()) {
            throw new IllegalStateException(REOPEN_HISTORICAL);
        }
        if (cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)) {
            throw new IllegalStateException(REOPEN_WITH_OPEN_SESSION);
        }
        boolean isLast = cashSessionRepository.findFirstByHistoricalImportIdIsNullOrderBySessionNumberDesc()
            .filter(last -> last.getId().equals(id))
            .isPresent();
        if (!isLast) {
            throw new IllegalStateException(REOPEN_ONLY_LAST);
        }

        Optional<FinancialTransaction> adjustment = lockClosingAdjustment(found);
        CashSession session = cashSessionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja no encontrada con ID: " + id));

        Instant now = Instant.now();
        CashSessionReopening reopening = cashSessionReopeningRepository.saveAndFlush(CashSessionReopening.builder()
            .cashSessionId(id)
            .reason(reason)
            .reopenedBy(username)
            .reopenedAt(now)
            .closingAmount(session.getClosingAmount())
            .closingComment(session.getClosingComment())
            .closedBy(session.getClosedBy())
            .closedAt(session.getClosedAt())
            .expectedCash(session.getExpectedCash())
            .difference(session.getDifference())
            .voidedAdjustmentId(adjustment.map(FinancialTransaction::getId).orElse(null))
            .build());

        adjustment.ifPresent(closing -> voidClosingAdjustment(closing, session, reason, username, now));
        cashDenominationCutRepository.moveToReopening(id, reopening.getId());

        session.setStatus(CashSessionStatus.ABIERTA);
        session.setClosingAmount(null);
        session.setClosingComment(null);
        session.setClosedBy(null);
        session.setClosedAt(null);
        session.setDifference(null);
        expectedCashCalculator.refresh(session);

        // The lock already orders this with openings; the index of the open session is the last defense
        CashSession reopened;
        try {
            reopened = cashSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(REOPEN_WITH_OPEN_SESSION);
        }
        return mapToSummary(reopened, saleRepository.countByCashSessionId(id));
    }

    // D3: with a difference there must be exactly one active closing adjustment, of that amount and direction; without
    // one, none. Checked before touching any balance: if they disagree, nothing changes
    private Optional<FinancialTransaction> lockClosingAdjustment(CashSession session) {
        Long number = session.getSessionNumber();
        List<FinancialTransaction> adjustments = financialTransactionRepository.findActiveClosingAdjustments(session.getId(),
            List.of(closingAdjustmentDescription(true, number), closingAdjustmentDescription(false, number)));
        BigDecimal difference = session.getDifference();
        if (difference == null || difference.signum() == 0) {
            if (!adjustments.isEmpty()) {
                throw new IllegalStateException(String.format(REOPEN_ADJUSTMENT_MISMATCH, number));
            }
            return Optional.empty();
        }
        boolean surplus = difference.signum() > 0;
        if (adjustments.size() != 1
                || adjustments.getFirst().getAmount().compareTo(difference.abs()) != 0
                || adjustments.getFirst().getType() != (surplus ? FinancialTransactionType.INGRESO_MANUAL : FinancialTransactionType.GASTO_MANUAL)
                || !adjustments.getFirst().getDescription().equals(closingAdjustmentDescription(surplus, number))) {
            throw new IllegalStateException(String.format(REOPEN_ADJUSTMENT_MISMATCH, number));
        }
        return financialTransactionRepository.findByIdForUpdate(adjustments.getFirst().getId());
    }

    private void voidClosingAdjustment(FinancialTransaction adjustment, CashSession session, String reason,
                                       String username, Instant now) {
        FinancialAccount cashAccount = lockCashAccount();
        // A surplus went in and a shortage went out: taking the signed difference away undoes either one
        cashAccount.setCurrentBalance(cashAccount.getCurrentBalance().subtract(session.getDifference())
            .setScale(2, RoundingMode.HALF_UP));
        financialAccountRepository.save(cashAccount);

        // The whole reason stays in the reopening
        String voidReason = "Reapertura de caja #" + session.getSessionNumber() + ": " + reason;
        adjustment.setStatus(FinancialTransactionStatus.ANULADO);
        adjustment.setVoidedAt(now);
        adjustment.setVoidedBy(username);
        adjustment.setVoidReason(voidReason.length() > VOID_REASON_MAX ? voidReason.substring(0, VOID_REASON_MAX) : voidReason);
        financialTransactionRepository.save(adjustment);
    }

    @Override
    @Transactional(readOnly = true)
    public CashSessionSummaryResponse getCurrentSession() {
        return findCurrentSession()
            .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna caja abierta actualmente."));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashSessionSummaryResponse> findCurrentSession() {
        return cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA).map(session -> {
            expectedCashCalculator.refresh(session);
            return mapToSummary(session, saleRepository.countByCashSessionId(session.getId()));
        });
    }

    @Override
    public CashSessionSummaryResponse closeCurrentSession(CloseCashRequest request, String username) {
        CashSession session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna caja abierta para cerrar."));

        BigDecimal closingAmount = request.closingAmount().setScale(2, RoundingMode.HALF_UP);
        List<CashDenominationCut> cuts = buildCuts(session, request.cuts(), closingAmount);
        BigDecimal expectedCash = expectedCashCalculator.calculate(session);
        BigDecimal difference = closingAmount.subtract(expectedCash).setScale(2, RoundingMode.HALF_UP);

        session.setClosingAmount(closingAmount);
        session.setClosingComment(request.closingComment() != null && !request.closingComment().isBlank()
            ? request.closingComment().trim() : null);
        session.setClosedBy(username);
        session.setClosedAt(Instant.now());
        session.setStatus(CashSessionStatus.CERRADA);
        session.setExpectedCash(expectedCash);
        session.setDifference(difference);

        // The optional count by bills and coins (CAJ-04)
        if (!cuts.isEmpty()) {
            session.getCuts().clear();
            session.getCuts().addAll(cuts);
        }

        CashSession closed = cashSessionRepository.save(session);
        recordClosingAdjustment(closed, difference, username);
        long salesCount = saleRepository.countByCashSessionId(closed.getId());
        return mapToSummary(closed, salesCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashSessionSummaryResponse> getAllSessions() {
        // CAJ-14: the last session that is not historical, when it is closed and no other is open
        UUID reopenableId = cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)
            ? null
            : cashSessionRepository.findFirstByHistoricalImportIdIsNullOrderBySessionNumberDesc()
                .map(CashSession::getId)
                .orElse(null);
        return cashSessionRepository.findAllByOrderByOpenedAtDesc().stream()
            .map(session -> {
                long count = saleRepository.countByCashSessionId(session.getId());
                return mapToSummary(session, count, session.getId().equals(reopenableId));
            })
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CashSessionDetailResponse getSessionDetail(UUID id) {
        CashSession session = cashSessionRepository.findByIdWithCuts(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja no encontrada con ID: " + id));

        List<Sale> sales = saleRepository.findByCashSessionIdOrderByCreatedAtDesc(id);

        List<SaleResponse> saleResponses = sales.stream()
            .map(SaleResponse::fromEntity)
            .collect(Collectors.toList());

        List<CashCutDto> cutDtos = session.getCuts() != null
            ? session.getCuts().stream()
                .map(CashSessionServiceImpl::cutDto)
                .collect(Collectors.toList())
            : Collections.emptyList();

        BigDecimal expectedCash = session.getStatus() == CashSessionStatus.ABIERTA
            ? expectedCashCalculator.calculate(session)
            : session.getExpectedCash();

        return new CashSessionDetailResponse(
            session.getId(),
            session.getSessionNumber(),
            session.getStatus(),
            session.getOpeningAmount(),
            session.getOpeningComment(),
            session.getOpenedBy(),
            session.getOpenedAt(),
            session.getClosingAmount(),
            session.getClosingComment(),
            session.getClosedBy(),
            session.getClosedAt(),
            session.getTotalSalesCash(),
            session.getTotalSalesQr(),
            session.getTotalSales(),
            expectedCash,
            session.getDifference(),
            sales.size(),
            cutDtos,
            saleResponses,
            session.isHistorical(),
            session.getHistoricalOtherIncome(),
            session.getHistoricalExpenses(),
            session.getHistoricalOtherOutflow(),
            session.getHistoricalInternalOutflow(),
            session.getOperationDate(),
            reopenings(id)
        );
    }

    private static CashCutDto cutDto(CashDenominationCut cut) {
        return new CashCutDto(cut.getDenomination(), cut.getCashQuantity(), cut.getReserveQuantity(), cut.getSubtotal());
    }

    // CAJ-14: oldest first, each one with the cuts of the closing it undid
    private List<CashSessionReopeningResponse> reopenings(UUID sessionId) {
        List<CashSessionReopening> reopenings = cashSessionReopeningRepository.findByCashSessionIdOrderByReopenedAtAsc(sessionId);
        if (reopenings.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CashDenominationCut>> cuts = cashDenominationCutRepository
            .findByReopeningIdInOrderByDenominationDesc(reopenings.stream().map(CashSessionReopening::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(CashDenominationCut::getReopeningId));
        return reopenings.stream()
            .map(reopening -> new CashSessionReopeningResponse(
                reopening.getId(),
                reopening.getReason(),
                reopening.getReopenedBy(),
                reopening.getReopenedAt(),
                reopening.getClosingAmount(),
                reopening.getClosingComment(),
                reopening.getClosedBy(),
                reopening.getClosedAt(),
                reopening.getExpectedCash(),
                reopening.getDifference(),
                cuts.getOrDefault(reopening.getId(), List.of()).stream().map(CashSessionServiceImpl::cutDto).toList()))
            .toList();
    }

    // CAJ-04: the count by bills and coins is optional, but when given it must add up to the counted amount, so
    // the history never shows a count that disagrees with the closing. Empty rows are left out
    public static List<CashDenominationCut> buildCuts(CashSession session, List<CashCutDto> requested, BigDecimal closingAmount) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<CashDenominationCut> cuts = new ArrayList<>();
        Set<BigDecimal> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CashCutDto cutDto : requested) {
            BigDecimal denomination = cutDto.denomination().setScale(2, RoundingMode.HALF_UP);
            if (!DENOMINATIONS.contains(denomination)) {
                throw new IllegalArgumentException(String.format(CUT_DENOMINATION_INVALID, StoreFormat.money(denomination)));
            }
            if (!seen.add(denomination)) {
                throw new IllegalArgumentException(String.format(CUT_DENOMINATION_REPEATED, StoreFormat.money(denomination)));
            }
            int cashQty = cutDto.cashQuantity() != null ? cutDto.cashQuantity() : 0;
            int reserveQty = cutDto.reserveQuantity() != null ? cutDto.reserveQuantity() : 0;
            if (cashQty + reserveQty == 0) {
                continue;
            }
            BigDecimal subtotal = denomination.multiply(BigDecimal.valueOf(cashQty + reserveQty)).setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal);
            cuts.add(CashDenominationCut.builder()
                .cashSession(session)
                .denomination(denomination)
                .cashQuantity(cashQty)
                .reserveQuantity(reserveQty)
                .subtotal(subtotal)
                .build());
        }
        if (!cuts.isEmpty() && total.compareTo(closingAmount) != 0) {
            throw new IllegalArgumentException(String.format(CUTS_MISMATCH, StoreFormat.money(total),
                StoreFormat.money(closingAmount)));
        }
        return cuts;
    }

    // CAJ-09: the difference goes to Caja as an Ajuste income (surplus) or expense (shortage) so its balance
    // matches the counted cash. It is linked to the closed session, so it cannot be edited or voided (CAJ-06); only
    // reopening the session voids it (CAJ-14).
    // Lock order T-14: the session is already locked, then the account.
    private void recordClosingAdjustment(CashSession session, BigDecimal difference, String username) {
        if (difference.signum() == 0) {
            return;
        }
        boolean surplus = difference.signum() > 0;
        FinancialCategory category = adjustmentCategory(surplus);
        recordAdjustment(session, lockCashAccount(), category, difference,
            closingAdjustmentDescription(surplus, session.getSessionNumber()),
            session.getClosingComment(), username);
    }

    // The only place this text is built: reopening finds the closing adjustment by it (CAJ-14)
    private static String closingAdjustmentDescription(boolean surplus, Long sessionNumber) {
        return (surplus ? "Sobrante" : "Faltante") + " al cierre de caja #" + sessionNumber;
    }

    // CAJ-10: the opening amount is the cash in the drawer, so the difference with the Caja balance (money taken
    // out or found between sessions, explained in the opening comment) goes to Caja as an Ajuste linked to the new
    // session. It is not cash of the session, so the expected cash ignores it (CAJ-03), and it is never voided.
    // Lock order T-14: the new session is already written, then the account.
    private void recordOpeningAdjustment(CashSession session, String username) {
        FinancialAccount cashAccount = lockCashAccount();
        BigDecimal difference = session.getOpeningAmount().subtract(cashAccount.getCurrentBalance())
            .setScale(2, RoundingMode.HALF_UP);
        if (difference.signum() == 0) {
            return;
        }
        recordAdjustment(session, cashAccount, adjustmentCategory(difference.signum() > 0), difference,
            "Diferencia al abrir la caja #" + session.getSessionNumber(), session.getOpeningComment(), username);
    }

    private FinancialCategory adjustmentCategory(boolean surplus) {
        return financialCategoryRepository
            .findBySystemKeyAndDeletedAtIsNull(surplus ? FinancialCategorySystemKey.CASH_SURPLUS : FinancialCategorySystemKey.CASH_SHORTAGE)
            .orElseThrow(() -> new IllegalStateException(ADJUSTMENT_CATEGORY_MISSING));
    }

    private FinancialAccount lockCashAccount() {
        UUID cashAccountId = financialAccountRepository.findByIsCashAccountTrue()
            .orElseThrow(() -> new IllegalStateException(CASH_ACCOUNT_NOT_FOUND))
            .getId();
        FinancialAccount cashAccount = financialAccountRepository.findAllByIdForUpdate(List.of(cashAccountId)).get(0);
        // Refreshed so a balance read before the lock is not used
        entityManager.refresh(cashAccount);
        return cashAccount;
    }

    private void recordAdjustment(CashSession session, FinancialAccount cashAccount, FinancialCategory category,
                                  BigDecimal difference, String description, String notes, String username) {
        boolean surplus = difference.signum() > 0;
        BigDecimal amount = difference.abs();
        cashAccount.setCurrentBalance(surplus
            ? cashAccount.getCurrentBalance().add(amount)
            : cashAccount.getCurrentBalance().subtract(amount));
        financialAccountRepository.save(cashAccount);

        Instant now = Instant.now();
        financialTransactionRepository.save(FinancialTransaction.builder()
            .type(surplus ? FinancialTransactionType.INGRESO_MANUAL : FinancialTransactionType.GASTO_MANUAL)
            .sourceAccount(cashAccount)
            .category(category)
            .amount(amount)
            .transactionDate(now)
            // CAJ-11: opening and closing adjustments count for the day of the session
            .operationDate(session.getOperationDate())
            .description(description)
            .notes(notes)
            .status(FinancialTransactionStatus.ACTIVO)
            .cashSessionId(session.getId())
            .createdBy(username)
            .createdAt(now)
            .build());
    }

    private CashSessionSummaryResponse mapToSummary(CashSession session, long salesCount) {
        return mapToSummary(session, salesCount, false);
    }

    private CashSessionSummaryResponse mapToSummary(CashSession session, long salesCount, boolean reopenable) {
        return new CashSessionSummaryResponse(
            session.getId(),
            session.getSessionNumber(),
            session.getStatus(),
            session.getOpeningAmount(),
            session.getOpeningComment(),
            session.getOpenedBy(),
            session.getOpenedAt(),
            session.getClosingAmount(),
            session.getClosingComment(),
            session.getClosedBy(),
            session.getClosedAt(),
            session.getTotalSalesCash(),
            session.getTotalSalesQr(),
            session.getTotalSales(),
            session.getExpectedCash(),
            session.getDifference(),
            salesCount,
            session.isHistorical(),
            session.getOperationDate(),
            reopenable
        );
    }
}
