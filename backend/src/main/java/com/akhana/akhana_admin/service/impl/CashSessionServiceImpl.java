package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.CashSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private static final String ALREADY_OPEN_MESSAGE = "Ya existe una caja abierta en el sistema. Debe cerrarse antes de iniciar una nueva.";
    static final String CASH_ACCOUNT_NOT_FOUND = "No se encontró la cuenta Caja en el sistema.";
    static final String ADJUSTMENT_CATEGORY_MISSING = "Falta la categoría de sistema Ajuste para registrar la diferencia de caja.";

    @Override
    public CashSessionSummaryResponse openSession(OpenCashRequest request, String username) {
        if (cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)) {
            throw new DuplicateResourceException(ALREADY_OPEN_MESSAGE);
        }

        BigDecimal openingAmount = request.openingAmount().setScale(2, RoundingMode.HALF_UP);

        CashSession session = CashSession.builder()
            .status(CashSessionStatus.ABIERTA)
            .openingAmount(openingAmount)
            .openingComment(request.openingComment() != null ? request.openingComment().trim() : null)
            .openedBy(username)
            .openedAt(Instant.now())
            .expectedCash(openingAmount)
            .totalSalesCash(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .totalSalesQr(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .totalSales(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .build();

        // A concurrent opening passes the check above; the partial unique index on ABIERTA rejects it here
        CashSession saved;
        try {
            saved = cashSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(ALREADY_OPEN_MESSAGE);
        }
        return mapToSummary(saved, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public CashSessionSummaryResponse getCurrentSession() {
        CashSession session = cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna caja abierta actualmente."));

        expectedCashCalculator.refresh(session);
        long salesCount = saleRepository.countByCashSessionId(session.getId());
        return mapToSummary(session, salesCount);
    }

    @Override
    public CashSessionSummaryResponse closeCurrentSession(CloseCashRequest request, String username) {
        CashSession session = cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna caja abierta para cerrar."));

        BigDecimal closingAmount = request.closingAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedCash = expectedCashCalculator.calculate(session);
        BigDecimal difference = closingAmount.subtract(expectedCash).setScale(2, RoundingMode.HALF_UP);

        session.setClosingAmount(closingAmount);
        session.setClosingComment(request.closingComment() != null ? request.closingComment().trim() : null);
        session.setClosedBy(username);
        session.setClosedAt(Instant.now());
        session.setStatus(CashSessionStatus.CERRADA);
        session.setExpectedCash(expectedCash);
        session.setDifference(difference);

        // Persistir cortes opcionales si fueron provistos
        if (request.cuts() != null && !request.cuts().isEmpty()) {
            session.getCuts().clear();
            for (CashCutDto cutDto : request.cuts()) {
                int cashQty = cutDto.cashQuantity() != null ? cutDto.cashQuantity() : 0;
                int reserveQty = cutDto.reserveQuantity() != null ? cutDto.reserveQuantity() : 0;
                BigDecimal denom = cutDto.denomination().setScale(2, RoundingMode.HALF_UP);
                BigDecimal subtotal = denom.multiply(BigDecimal.valueOf(cashQty + reserveQty)).setScale(2, RoundingMode.HALF_UP);

                CashDenominationCut cut = CashDenominationCut.builder()
                    .cashSession(session)
                    .denomination(denom)
                    .cashQuantity(cashQty)
                    .reserveQuantity(reserveQty)
                    .subtotal(subtotal)
                    .build();

                session.getCuts().add(cut);
            }
        }

        CashSession closed = cashSessionRepository.save(session);
        recordClosingAdjustment(closed, difference, username);
        long salesCount = saleRepository.countByCashSessionId(closed.getId());
        return mapToSummary(closed, salesCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashSessionSummaryResponse> getAllSessions() {
        return cashSessionRepository.findAllByOrderByOpenedAtDesc().stream()
            .map(session -> {
                long count = saleRepository.countByCashSessionId(session.getId());
                return mapToSummary(session, count);
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
                .map(c -> new CashCutDto(
                    c.getDenomination(),
                    c.getCashQuantity(),
                    c.getReserveQuantity(),
                    c.getSubtotal()
                ))
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
            saleResponses
        );
    }

    // CAJ-09: the difference goes to Caja as an Ajuste income (surplus) or expense (shortage) so its balance
    // matches the counted cash. It is linked to the closed session, so it cannot be edited or voided (CAJ-06).
    // Lock order T-14: the session is already locked, then the account.
    private void recordClosingAdjustment(CashSession session, BigDecimal difference, String username) {
        if (difference.signum() == 0) {
            return;
        }
        boolean surplus = difference.signum() > 0;
        FinancialCategory category = financialCategoryRepository
            .findBySystemKeyAndDeletedAtIsNull(surplus ? FinancialCategorySystemKey.CASH_SURPLUS : FinancialCategorySystemKey.CASH_SHORTAGE)
            .orElseThrow(() -> new IllegalStateException(ADJUSTMENT_CATEGORY_MISSING));
        UUID cashAccountId = financialAccountRepository.findByIsCashAccountTrue()
            .orElseThrow(() -> new IllegalStateException(CASH_ACCOUNT_NOT_FOUND))
            .getId();
        FinancialAccount cashAccount = financialAccountRepository.findAllByIdForUpdate(List.of(cashAccountId)).get(0);

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
            .description((surplus ? "Sobrante" : "Faltante") + " al cierre de caja #" + session.getSessionNumber())
            .notes(session.getClosingComment())
            .status(FinancialTransactionStatus.ACTIVO)
            .cashSessionId(session.getId())
            .createdBy(username)
            .createdAt(now)
            .build());
    }

    private CashSessionSummaryResponse mapToSummary(CashSession session, long salesCount) {
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
            salesCount
        );
    }
}
