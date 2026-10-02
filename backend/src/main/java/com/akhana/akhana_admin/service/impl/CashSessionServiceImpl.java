package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.CashSessionService;
import lombok.RequiredArgsConstructor;
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

    @Override
    public CashSessionSummaryResponse openSession(OpenCashRequest request, String username) {
        if (cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)) {
            throw new DuplicateResourceException("Ya existe una caja abierta en el sistema. Debe cerrarse antes de iniciar una nueva.");
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

        CashSession saved = cashSessionRepository.save(session);
        return mapToSummary(saved, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public CashSessionSummaryResponse getCurrentSession() {
        CashSession session = cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna caja abierta actualmente."));

        long salesCount = saleRepository.countByCashSessionId(session.getId());
        return mapToSummary(session, salesCount);
    }

    @Override
    public SaleResponse registerSaleInCurrentSession(SaleRequest request, String username) {
        CashSession session = cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new IllegalStateException("No se pueden registrar ventas porque no existe una caja abierta actualmente."));

        BigDecimal amount = request.totalAmount().setScale(2, RoundingMode.HALF_UP);
        long currentSalesCount = saleRepository.countByCashSessionId(session.getId());
        Long sessionNum = session.getSessionNumber() != null ? session.getSessionNumber() : 1L;
        String saleNumber = String.format("VTA-%04d-%03d", sessionNum, currentSalesCount + 1);

        BigDecimal amountCash = request.paymentMethod() == PaymentMethod.EFECTIVO ? amount : BigDecimal.ZERO;
        BigDecimal amountQr = request.paymentMethod() == PaymentMethod.QR ? amount : BigDecimal.ZERO;

        Sale sale = Sale.builder()
            .saleNumber(saleNumber)
            .cashSession(session)
            .status(SaleStatus.COMPLETADA)
            .subtotalAmount(amount)
            .discountItemsTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .globalDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .discountTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .totalAmount(amount)
            .paymentMethod(request.paymentMethod())
            .amountCash(amountCash)
            .amountQr(amountQr)
            .amountReceived(amountCash)
            .changeGiven(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
            .description(request.description() != null ? request.description().trim() : null)
            .createdBy(username)
            .createdAt(Instant.now())
            .build();

        Sale savedSale = saleRepository.save(sale);

        // Actualizar métricas acumuladas de la caja activa
        if (request.paymentMethod() == PaymentMethod.EFECTIVO) {
            session.setTotalSalesCash(session.getTotalSalesCash().add(amount));
            session.setExpectedCash(session.getOpeningAmount().add(session.getTotalSalesCash()));
        } else if (request.paymentMethod() == PaymentMethod.QR) {
            session.setTotalSalesQr(session.getTotalSalesQr().add(amount));
        }
        session.setTotalSales(session.getTotalSales().add(amount));

        cashSessionRepository.save(session);

        return SaleResponse.fromEntity(savedSale);
    }

    @Override
    public CashSessionSummaryResponse closeCurrentSession(CloseCashRequest request, String username) {
        CashSession session = cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)
            .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna caja abierta para cerrar."));

        BigDecimal closingAmount = request.closingAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedCash = session.getExpectedCash().setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = closingAmount.subtract(expectedCash).setScale(2, RoundingMode.HALF_UP);

        session.setClosingAmount(closingAmount);
        session.setClosingComment(request.closingComment() != null ? request.closingComment().trim() : null);
        session.setClosedBy(username);
        session.setClosedAt(Instant.now());
        session.setStatus(CashSessionStatus.CERRADA);
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
            session.getExpectedCash(),
            session.getDifference(),
            sales.size(),
            cutDtos,
            saleResponses
        );
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
