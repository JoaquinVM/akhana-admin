package com.akhana.akhana_admin.service;

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
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import com.akhana.akhana_admin.service.impl.CashSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import jakarta.persistence.EntityManager;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashSessionServiceTest {

    @Mock
    private CashSessionRepository cashSessionRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialCategoryRepository financialCategoryRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private CashNoAttentionDayRepository cashNoAttentionDayRepository;

    @Mock
    private CashSessionReopeningRepository cashSessionReopeningRepository;

    @Mock
    private CashDenominationCutRepository cashDenominationCutRepository;

    private CashSessionServiceImpl cashSessionService;

    private UUID sessionId;
    private CashSession activeSession;

    @BeforeEach
    void setUp() {
        cashSessionService = new CashSessionServiceImpl(cashSessionRepository, saleRepository,
            new ExpectedCashCalculator(financialTransactionRepository),
            financialAccountRepository, financialCategoryRepository, financialTransactionRepository, entityManager,
            cashNoAttentionDayRepository, cashSessionReopeningRepository, cashDenominationCutRepository);
        sessionId = UUID.randomUUID();
        activeSession = CashSession.builder()
            .id(sessionId)
            .sessionNumber(1L)
            .status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00"))
            .openingComment("Fondo inicial")
            .openedBy("admin")
            .openedAt(Instant.now())
            .expectedCash(new BigDecimal("100.00"))
            .totalSalesCash(BigDecimal.ZERO)
            .totalSalesQr(BigDecimal.ZERO)
            .totalSales(BigDecimal.ZERO)
            .cuts(new ArrayList<>())
            .sales(new ArrayList<>())
            .build();
    }

    @Test
    @DisplayName("Debe abrir exitosamente una caja cuando no existe ninguna sesión abierta")
    void openSession_success() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        stubCashAccount("150.00");
        when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> {
            CashSession cs = invocation.getArgument(0);
            cs.setId(sessionId);
            cs.setSessionNumber(1L);
            return cs;
        });

        OpenCashRequest request = new OpenCashRequest(new BigDecimal("150.00"), "Apertura de turno");
        CashSessionSummaryResponse response = cashSessionService.openSession(request, "cajero1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(CashSessionStatus.ABIERTA);
        assertThat(response.openingAmount()).isEqualByComparingTo("150.00");
        assertThat(response.expectedCash()).isEqualByComparingTo("150.00");
        assertThat(response.openedBy()).isEqualTo("cajero1");
        // CAJ-11: its day of operation is the store day it was opened
        assertThat(response.operationDate()).isEqualTo(StoreFormat.storeDay(response.openedAt()));
    }

    private CashSession closedSession(String closingAmount) {
        return CashSession.builder()
            .id(UUID.randomUUID())
            .sessionNumber(7L)
            .status(CashSessionStatus.CERRADA)
            .openingAmount(new BigDecimal("100.00"))
            .closingAmount(new BigDecimal(closingAmount))
            .closedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("CAJ-10: propone como monto inicial el cierre de la caja anterior")
    void getOpeningProposal_returnsPreviousClosing() {
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.5")));

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.proposedAmount()).isEqualByComparingTo("245.50");
        assertThat(proposal.previousSessionNumber()).isEqualTo(7L);
    }

    @Test
    @DisplayName("CAJ-10: sin caja anterior ni cuenta Caja propone 0.00")
    void getOpeningProposal_noPreviousSession_returnsZero() {
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.empty());

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.proposedAmount()).isEqualByComparingTo("0.00");
        assertThat(proposal.previousSessionNumber()).isNull();
    }

    @Test
    @DisplayName("FIN-01: la primera caja propone el saldo al corte de Caja")
    void getOpeningProposal_noPreviousSession_returnsCashBalance() {
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.empty());
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(FinancialAccount.builder()
            .isCashAccount(true).currentBalance(new BigDecimal("7637.9")).build()));

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.proposedAmount()).isEqualByComparingTo("7637.90");
        assertThat(proposal.previousSessionNumber()).isNull();
    }

    @Test
    @DisplayName("CAJ-10: abrir con el monto propuesto no exige comentario")
    void openSession_withProposedAmount_noCommentNeeded() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        stubCashAccount("245.50");
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.50")));
        when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashSessionSummaryResponse response = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal("245.5"), "  "), "cajero1");

        assertThat(response.openingAmount()).isEqualByComparingTo("245.50");
        assertThat(response.openingComment()).isNull();
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-10: abrir con un monto distinto al cierre anterior sin comentario se rechaza")
    void openSession_changedAmountWithoutComment_throws() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.50")));

        assertThatThrownBy(() -> cashSessionService.openSession(
                new OpenCashRequest(new BigDecimal("200.00"), " "), "cajero1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Bs 245,50")
            .hasMessageContaining("comentario de apertura");
        verify(cashSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("CAJ-10: abrir con un monto distinto al cierre anterior y comentario se acepta")
    void openSession_changedAmountWithComment_succeeds() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.50")));
        when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> {
            CashSession cs = invocation.getArgument(0);
            cs.setId(sessionId);
            cs.setSessionNumber(8L);
            return cs;
        });
        FinancialAccount cash = stubAdjustment(FinancialCategorySystemKey.CASH_SHORTAGE, FinancialCategoryType.GASTO, "245.50");

        CashSessionSummaryResponse response = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal("200.00"), " Se depositaron 45.50 "), "cajero1");

        assertThat(response.openingAmount()).isEqualByComparingTo("200.00");
        assertThat(response.openingComment()).isEqualTo("Se depositaron 45.50");
        // CAJ-10: la diferencia con el saldo de Caja queda como Ajuste de la caja nueva, así Caja = lo que hay
        FinancialTransaction tx = captureAdjustment();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.GASTO_MANUAL);
        assertThat(tx.getAmount()).isEqualByComparingTo("45.50");
        assertThat(tx.getCategory().getSystemKey()).isEqualTo(FinancialCategorySystemKey.CASH_SHORTAGE);
        assertThat(tx.getCashSessionId()).isEqualTo(sessionId);
        assertThat(tx.getDescription()).isEqualTo("Diferencia al abrir la caja #8");
        assertThat(tx.getNotes()).isEqualTo("Se depositaron 45.50");
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("200.00");
        // El efectivo esperado de la caja nueva es el monto inicial: el ajuste no es efectivo de la sesión
        assertThat(response.expectedCash()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("CAJ-10: abrir con más que el saldo de Caja registra un ingreso de Ajuste")
    void openSession_moreThanCashBalance_recordsIncomeAdjustment() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("100.00")));
        when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FinancialAccount cash = stubAdjustment(FinancialCategorySystemKey.CASH_SURPLUS, FinancialCategoryType.INGRESO, "100.00");

        cashSessionService.openSession(new OpenCashRequest(new BigDecimal("120.00"), "Apareció un billete"), "cajero1");

        FinancialTransaction tx = captureAdjustment();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.INGRESO_MANUAL);
        assertThat(tx.getAmount()).isEqualByComparingTo("20.00");
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("120.00");
        // CAJ-11: the opening adjustment counts for the day of the new session
        assertThat(tx.getOperationDate()).isEqualTo(StoreFormat.storeDay(tx.getTransactionDate()));
    }

    @Test
    @DisplayName("CAJ-10: en la primera caja, abrir con un monto distinto al saldo de Caja exige comentario")
    void openSession_firstSessionChangedAmountWithoutComment_throws() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        stubCashAccount("300.00");

        assertThatThrownBy(() -> cashSessionService.openSession(
                new OpenCashRequest(new BigDecimal("250.00"), null), "cajero1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("saldo de la cuenta Caja (Bs 300,00)");
        verify(cashSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Debe lanzar excepción al intentar abrir caja si ya existe una abierta")
    void openSession_alreadyOpen_throwsException() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(true);

        OpenCashRequest request = new OpenCashRequest(new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> cashSessionService.openSession(request, "admin"))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Ya existe una caja abierta");
        verify(cashSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Debe rechazar la apertura si otra caja se abrió al mismo tiempo (índice único de caja ABIERTA)")
    void openSession_concurrentOpening_throwsDuplicate() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        stubCashAccount("100.00");
        when(cashSessionRepository.saveAndFlush(any(CashSession.class)))
            .thenThrow(new DataIntegrityViolationException("idx_cash_sessions_only_one_open"));

        OpenCashRequest request = new OpenCashRequest(new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> cashSessionService.openSession(request, "admin"))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("Ya existe una caja abierta");
    }

    @Test
    @DisplayName("Debe retornar la sesión activa actual con el contador de ventas")
    void getCurrentSession_success() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(3L);

        CashSessionSummaryResponse response = cashSessionService.getCurrentSession();

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.salesCount()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(CashSessionStatus.ABIERTA);
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si no hay caja abierta al consultar actual")
    void getCurrentSession_notFound_throwsException() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashSessionService.getCurrentSession())
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("No existe ninguna caja abierta");
    }

    @Test
    @DisplayName("Debe cerrar caja y calcular la diferencia frente al efectivo esperado")
    void closeSession_withDifferenceAndCuts_success() {
        stubAdjustment(FinancialCategorySystemKey.CASH_SURPLUS, FinancialCategoryType.INGRESO, "100.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(2L);

        List<CashCutDto> cuts = List.of(
            new CashCutDto(new BigDecimal("100.00"), 1, 0, new BigDecimal("100.00")),
            new CashCutDto(new BigDecimal("20.00"), 1, 0, new BigDecimal("20.00"))
        );

        // Expected era 100.00, cerramos con 120.00 -> diferencia +20.00
        CloseCashRequest request = new CloseCashRequest(new BigDecimal("120.00"), "Cierre de turno con sobrante", cuts, null, null);
        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(request, "cajero1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(CashSessionStatus.CERRADA);
        assertThat(response.closingAmount()).isEqualByComparingTo("120.00");
        assertThat(response.difference()).isEqualByComparingTo("20.00");
        assertThat(response.closedBy()).isEqualTo("cajero1");
        assertThat(activeSession.getCuts()).hasSize(2);
    }

    @Test
    @DisplayName("CAJ-04: los cortes que no suman el monto contado se rechazan y la caja sigue abierta")
    void closeSession_cutsThatDoNotAddUp_rejected() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        List<CashCutDto> cuts = List.of(new CashCutDto(new BigDecimal("100.00"), 1, 0, null));

        assertThatThrownBy(() -> cashSessionService.closeCurrentSession(
                new CloseCashRequest(new BigDecimal("3500.00"), null, cuts, null, null), "cajero1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Los cortes suman Bs 100,00 y el monto contado es Bs 3.500,00; corrija el conteo o deje los cortes vacíos.");
        assertThat(activeSession.getStatus()).isEqualTo(CashSessionStatus.ABIERTA);
        verify(cashSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-04: un corte que no es billete ni moneda boliviana, o repetido, se rechaza; las filas en 0 no se guardan")
    void closeSession_cutsDenominations() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> cashSessionService.closeCurrentSession(new CloseCashRequest(new BigDecimal("3.00"), null,
                List.of(new CashCutDto(new BigDecimal("3"), 1, 0, null)), null, null), "cajero1"))
            .hasMessage("El corte de Bs 3,00 no existe; use Bs 200, 100, 50, 20, 10, 5, 2, 1, 0,50, 0,20 o 0,10.");
        assertThatThrownBy(() -> cashSessionService.closeCurrentSession(new CloseCashRequest(new BigDecimal("20.00"), null,
                List.of(new CashCutDto(new BigDecimal("10"), 1, 0, null), new CashCutDto(new BigDecimal("10.00"), 0, 1, null)), null, null), "cajero1"))
            .hasMessage("El corte de Bs 10,00 está repetido.");

        List<CashDenominationCut> cuts = CashSessionServiceImpl.buildCuts(activeSession, List.of(
            new CashCutDto(new BigDecimal("0.5"), 3, 1, null),
            new CashCutDto(new BigDecimal("200"), 0, 0, null)), new BigDecimal("2.00"));
        assertThat(cuts).hasSize(1);
        assertThat(cuts.get(0).getSubtotal()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("CAJ-04: las reservas de billetes y monedas se suman a los cortes: 1.000 + 150 + 200 = 1.350 y se guardan con el cierre")
    void closeSession_reservesAddUpWithTheCuts() {
        stubAdjustment(FinancialCategorySystemKey.CASH_SURPLUS, FinancialCategoryType.INGRESO, "100.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(new CloseCashRequest(
            new BigDecimal("1350.00"), null, List.of(new CashCutDto(new BigDecimal("200"), 4, 1, null)),
            new BigDecimal("150"), new BigDecimal("200.00")), "cajero1");

        assertThat(response.status()).isEqualTo(CashSessionStatus.CERRADA);
        assertThat(response.closingAmount()).isEqualByComparingTo("1350.00");
        assertThat(response.difference()).isEqualByComparingTo("1250.00");
        assertThat(activeSession.getCuts()).hasSize(1);
        assertThat(activeSession.getClosingReserveBills()).isEqualByComparingTo("150.00");
        assertThat(activeSession.getClosingReserveCoins()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("CAJ-04: cortes y reservas que no suman el monto contado se rechazan y la caja sigue abierta")
    void closeSession_reservesThatDoNotAddUp_rejected() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> cashSessionService.closeCurrentSession(new CloseCashRequest(
                new BigDecimal("1300.00"), null, List.of(new CashCutDto(new BigDecimal("200"), 4, 1, null)),
                new BigDecimal("150.00"), new BigDecimal("200.00")), "cajero1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Los cortes y las reservas suman Bs 1.350,00 y el monto contado es Bs 1.300,00; corrija el conteo o deje los cortes y las reservas vacíos.");
        assertThat(activeSession.getStatus()).isEqualTo(CashSessionStatus.ABIERTA);
        assertThat(activeSession.getClosingReserveBills()).isNull();
        verify(cashSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-04: solo con reservas, sin cortes, el conteo son las reservas; una reserva en 0 no se guarda")
    void closeSession_onlyReserves() {
        stubAdjustment(FinancialCategorySystemKey.CASH_SURPLUS, FinancialCategoryType.INGRESO, "100.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cashSessionService.closeCurrentSession(new CloseCashRequest(new BigDecimal("150.00"), null, List.of(),
            new BigDecimal("150.00"), BigDecimal.ZERO), "cajero1");

        assertThat(activeSession.getStatus()).isEqualTo(CashSessionStatus.CERRADA);
        assertThat(activeSession.getCuts()).isEmpty();
        assertThat(activeSession.getClosingReserveBills()).isEqualByComparingTo("150.00");
        assertThat(activeSession.getClosingReserveCoins()).isNull();
    }

    @Test
    @DisplayName("CAJ-04: reservas sin cortes que no suman el monto contado se rechazan")
    void closeSession_onlyReservesThatDoNotAddUp_rejected() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> cashSessionService.closeCurrentSession(new CloseCashRequest(new BigDecimal("500.00"), null,
                null, new BigDecimal("150.00"), new BigDecimal("200.00")), "cajero1"))
            .hasMessage("Los cortes y las reservas suman Bs 350,00 y el monto contado es Bs 500,00; corrija el conteo o deje los cortes y las reservas vacíos.");
        verify(cashSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe obtener el historial de todas las sesiones de caja")
    void getAllSessions_success() {
        when(cashSessionRepository.findAllByOrderByOpenedAtDesc()).thenReturn(List.of(activeSession));
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(1L);

        List<CashSessionSummaryResponse> list = cashSessionService.getAllSessions();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("Debe obtener el detalle completo de una sesión de caja")
    void getSessionDetail_success() {
        when(cashSessionRepository.findByIdWithCuts(sessionId)).thenReturn(Optional.of(activeSession));
        Sale mockSale = Sale.builder()
            .id(UUID.randomUUID())
            .saleNumber("VTA-0001-001")
            .cashSession(activeSession)
            .totalAmount(new BigDecimal("35.00"))
            .paymentMethod(PaymentMethod.EFECTIVO)
            .createdBy("admin")
            .createdAt(Instant.now())
            .build();
        when(saleRepository.findByCashSessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(List.of(mockSale));

        CashSessionDetailResponse detail = cashSessionService.getSessionDetail(sessionId);

        assertThat(detail).isNotNull();
        assertThat(detail.id()).isEqualTo(sessionId);
        assertThat(detail.sales()).hasSize(1);
        assertThat(detail.sales().get(0).saleNumber()).isEqualTo("VTA-0001-001");
        assertThat(detail.closingReserveBills()).isNull();
    }

    @Test
    @DisplayName("CAJ-04, CAJ-07: el detalle de la caja trae las reservas de su cierre")
    void getSessionDetail_withReserves() {
        activeSession.setStatus(CashSessionStatus.CERRADA);
        activeSession.setClosingReserveBills(new BigDecimal("150.00"));
        activeSession.setClosingReserveCoins(new BigDecimal("200.00"));
        when(cashSessionRepository.findByIdWithCuts(sessionId)).thenReturn(Optional.of(activeSession));

        CashSessionDetailResponse detail = cashSessionService.getSessionDetail(sessionId);

        assertThat(detail.closingReserveBills()).isEqualByComparingTo("150.00");
        assertThat(detail.closingReserveCoins()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Debe calcular expectedCash descontando los gastos activos de Caja (monto inicial + efectivo - gastos)")
    void getCurrentSession_withActiveExpenses_discountsFromExpectedCash() {
        activeSession.setOpeningAmount(new BigDecimal("200.00"));
        activeSession.setTotalSalesCash(new BigDecimal("850.00"));
        activeSession.setTotalSalesQr(new BigDecimal("300.00"));
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(5L);
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(sessionId))
                .thenReturn(new BigDecimal("120.00"));

        CashSessionSummaryResponse response = cashSessionService.getCurrentSession();

        // 200 inicial + 850 efectivo - 120 gastos = 930 (las ventas QR no afectan)
        assertThat(response.expectedCash()).isEqualByComparingTo("930.00");
    }

    @Test
    @DisplayName("Debe cerrar caja y calcular la diferencia considerando los gastos activos descontados")
    void closeSession_withActiveExpenses_calculatesDifferenceAccurately() {
        stubAdjustment(FinancialCategorySystemKey.CASH_SURPLUS, FinancialCategoryType.INGRESO, "930.00");
        activeSession.setOpeningAmount(new BigDecimal("200.00"));
        activeSession.setTotalSalesCash(new BigDecimal("850.00"));
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(5L);
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(sessionId))
                .thenReturn(new BigDecimal("120.00"));

        // expectedCash es 930.00. Si se cuentan 950.00, hay sobrante de +20.00
        CloseCashRequest request = new CloseCashRequest(
            new BigDecimal("950.00"),
            "Cierre con gastos",
            Collections.emptyList(),
            null,
            null
        );

        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(request, "admin");

        assertThat(response.expectedCash()).isEqualByComparingTo("930.00");
        assertThat(response.difference()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Cierre con transferencias desde y hacia Caja: el esperado las incluye (CAJ-03, T-15)")
    void closeSession_withTransfers_includesThemInExpectedCash() {
        activeSession.setOpeningAmount(new BigDecimal("200.00"));
        activeSession.setTotalSalesCash(new BigDecimal("850.00"));
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // 120 de gastos + 300 retirados a Efectivo; 50 de cambio ingresado a Caja
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(sessionId)).thenReturn(new BigDecimal("420.00"));
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(sessionId)).thenReturn(new BigDecimal("50.00"));

        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal("680.00"), null, Collections.emptyList(), null, null), "admin");

        assertThat(response.expectedCash()).isEqualByComparingTo("680.00");
        assertThat(response.difference()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("CAJ-09: un sobrante se registra como ingreso de Ajuste en Caja vinculado a la caja cerrada")
    void closeSession_withSurplus_recordsIncomeAdjustment() {
        FinancialAccount cash = stubAdjustment(FinancialCategorySystemKey.CASH_SURPLUS, FinancialCategoryType.INGRESO, "100.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal("112.50"), "Sobró sencillo", Collections.emptyList(), null, null), "cajero1");

        FinancialTransaction tx = captureAdjustment();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.INGRESO_MANUAL);
        assertThat(tx.getAmount()).isEqualByComparingTo("12.50");
        assertThat(tx.getSourceAccount()).isSameAs(cash);
        assertThat(tx.getCategory().getSystemKey()).isEqualTo(FinancialCategorySystemKey.CASH_SURPLUS);
        assertThat(tx.getCashSessionId()).isEqualTo(sessionId);
        assertThat(tx.getDescription()).isEqualTo("Sobrante al cierre de caja #1");
        assertThat(tx.getNotes()).isEqualTo("Sobró sencillo");
        assertThat(tx.getCreatedBy()).isEqualTo("cajero1");
        // El saldo de Caja queda igual a lo contado
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("112.50");
    }

    @Test
    @DisplayName("CAJ-09, CAJ-11: un faltante se registra como gasto de Ajuste en Caja, baja su saldo y cuenta para el día de la caja aunque se cierre al día siguiente")
    void closeSession_withShortage_recordsExpenseAdjustment() {
        // Opened on 31-10 and closed now (the next day or later): the closing keeps its real time
        activeSession.setOperationDate(LocalDate.of(2026, 10, 31));
        FinancialAccount cash = stubAdjustment(FinancialCategorySystemKey.CASH_SHORTAGE, FinancialCategoryType.GASTO, "100.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal("93.00"), null, Collections.emptyList(), null, null), "cajero1");

        assertThat(response.difference()).isEqualByComparingTo("-7.00");
        FinancialTransaction tx = captureAdjustment();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.GASTO_MANUAL);
        assertThat(tx.getAmount()).isEqualByComparingTo("7.00");
        assertThat(tx.getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(tx.getTransactionDate()).isAfter(Instant.parse("2026-10-31T12:00:00Z"));
        assertThat(activeSession.getClosedAt()).isAfter(Instant.parse("2026-10-31T12:00:00Z"));
        assertThat(activeSession.getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(tx.getCategory().getSystemKey()).isEqualTo(FinancialCategorySystemKey.CASH_SHORTAGE);
        assertThat(tx.getDescription()).isEqualTo("Faltante al cierre de caja #1");
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("93.00");
    }

    @Test
    @DisplayName("CAJ-09: con cuadre exacto no se registra ningún ajuste")
    void closeSession_exact_recordsNoAdjustment() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal("100.00"), null, Collections.emptyList(), null, null), "cajero1");

        verify(financialTransactionRepository, never()).save(any());
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-09: sin la categoría de sistema Ajuste el cierre falla y no se toca la cuenta")
    void closeSession_withoutAdjustmentCategory_fails() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.CASH_SURPLUS))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal("120.00"), null, Collections.emptyList(), null, null), "cajero1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Ajuste");
        verify(financialAccountRepository, never()).save(any());
    }

    // --- CAJ-12: choosing the day ---

    // The example of the spec: Friday 30/10, Saturday 31/10, Sunday 1/11, Monday 2/11, Tuesday 3/11 (2020 has the same
    // calendar as 2026 and is always in the past)
    private static final LocalDate FRI_30 = LocalDate.of(2020, 10, 30);
    private static final LocalDate SAT_31 = LocalDate.of(2020, 10, 31);
    private static final LocalDate SUN_1 = LocalDate.of(2020, 11, 1);
    private static final LocalDate MON_2 = LocalDate.of(2020, 11, 2);
    private static final LocalDate TUE_3 = LocalDate.of(2020, 11, 3);

    private CashSession lastSession(long number, LocalDate day) {
        CashSession last = closedSession("150.00");
        last.setSessionNumber(number);
        last.setOperationDate(day);
        last.setExpectedCash(new BigDecimal("150.00"));
        return last;
    }

    private void stubLastSession(CashSession last) {
        when(cashSessionRepository.findFirstByOrderByOperationDateDescSessionNumberDesc()).thenReturn(Optional.of(last));
        lenient().when(cashSessionRepository.existsByHistoricalImportIdIsNull()).thenReturn(!last.isHistorical());
    }

    private void stubOpeningGoesThrough() {
        lenient().when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("150.00")));
        stubCashAccount("150.00");
        lenient().when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> {
            CashSession cs = invocation.getArgument(0);
            cs.setId(sessionId);
            cs.setSessionNumber(149L);
            return cs;
        });
    }

    private static OpenCashRequest openOn(LocalDate day, String comment) {
        return new OpenCashRequest(new BigDecimal("150.00"), comment, day, null, null);
    }

    private void assertOpeningRejected(OpenCashRequest request, String message) {
        assertThatThrownBy(() -> cashSessionService.openSession(request, "cajero1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(message);
        verify(cashSessionRepository, never()).saveAndFlush(any());
        verify(cashNoAttentionDayRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("CAJ-12: abrir con el día de hoy no pide comentario por el día")
    void openSession_today_noCommentNeeded() {
        LocalDate today = StoreFormat.today();
        stubLastSession(lastSession(148, today.minusDays(1)));
        stubOpeningGoesThrough();

        CashSessionSummaryResponse response = cashSessionService.openSession(openOn(today, null), "cajero1");

        assertThat(response.operationDate()).isEqualTo(today);
        assertThat(response.openingComment()).isNull();
    }

    @Test
    @DisplayName("CAJ-12: sin día en el pedido, la caja es de hoy")
    void openSession_noDay_isToday() {
        LocalDate today = StoreFormat.today();
        stubLastSession(lastSession(148, today.minusDays(1)));
        stubOpeningGoesThrough();

        CashSessionSummaryResponse response = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal("150.00"), null), "cajero1");

        assertThat(response.operationDate()).isEqualTo(today);
    }

    @Test
    @DisplayName("CAJ-12: un día anterior sin comentario se rechaza")
    void openSession_pastDayWithoutComment_rejected() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(openOn(SAT_31, "   "), "Elegir un día anterior pide un comentario.");
    }

    @Test
    @DisplayName("CAJ-12, CAJ-10: un solo comentario basta para el día anterior y el monto cambiado")
    void openSession_pastDayAndChangedAmount_oneComment() {
        stubLastSession(lastSession(148, FRI_30));
        stubOpeningGoesThrough();
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.CASH_SHORTAGE))
            .thenReturn(Optional.of(FinancialCategory.builder().id(UUID.randomUUID()).name("Ajuste")
                .type(FinancialCategoryType.GASTO).systemKey(FinancialCategorySystemKey.CASH_SHORTAGE).build()));

        CashSessionSummaryResponse response = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal("100.00"), " Carga atrasada ", SAT_31, null, null), "cajero1");

        assertThat(response.openingComment()).isEqualTo("Carga atrasada");
        // CAJ-11: everything of the session counts for its day; the opening keeps the real date and time and who opened
        assertThat(response.operationDate()).isEqualTo(SAT_31);
        assertThat(StoreFormat.storeDay(response.openedAt())).isEqualTo(StoreFormat.today());
        assertThat(response.openedBy()).isEqualTo("cajero1");
        assertThat(captureAdjustment().getOperationDate()).isEqualTo(SAT_31);
    }

    @Test
    @DisplayName("CAJ-12: un día futuro se rechaza, también llamando directo a la API")
    void openSession_futureDay_rejected() {
        assertOpeningRejected(openOn(StoreFormat.today().plusDays(1), "Mañana"), "El día de la caja no puede ser futuro.");
    }

    @Test
    @DisplayName("CAJ-12: un día igual al de la última caja se rechaza")
    void openSession_sameDayAsLast_rejected() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(openOn(FRI_30, "Otra"), "El día debe ser posterior al de la última caja (#148, 30/10).");
    }

    @Test
    @DisplayName("CAJ-12: un día anterior al de la última caja se rechaza, con el mes en dos dígitos y el día sin cero")
    void openSession_beforeLast_rejected() {
        stubLastSession(lastSession(150, MON_2));

        assertOpeningRejected(openOn(SAT_31, "Otra"), "El día debe ser posterior al de la última caja (#150, 2/11).");
    }

    @Test
    @DisplayName("CAJ-12: la regla también mira a la última caja si es histórica")
    void openSession_sameDayAsHistorical_rejected() {
        CashSession historical = lastSession(90, FRI_30);
        historical.setHistoricalImportId(UUID.randomUUID());
        stubLastSession(historical);

        assertOpeningRejected(openOn(FRI_30, "Otra"), "El día debe ser posterior al de la última caja (#90, 30/10).");
    }

    @Test
    @DisplayName("CAJ-12: si la caja de hoy está cerrada no se abre otra hoy")
    void openSession_todayAlreadyClosed_rejected() {
        LocalDate today = StoreFormat.today();
        stubLastSession(lastSession(151, today));

        assertOpeningRejected(openOn(today, null), "El día debe ser posterior al de la última caja (#151, "
            + StoreFormat.dayMonth(today) + ").");
    }

    @Test
    @DisplayName("CAJ-12: el índice de un día por caja responde con el mensaje del día, no con el de caja abierta")
    void openSession_dayIndexViolated_answersDayTaken() {
        stubLastSession(lastSession(148, StoreFormat.today().minusDays(1)));
        when(cashSessionRepository.findFirstByStatusAndHistoricalImportIdIsNullOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("150.00")));
        when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenThrow(new DataIntegrityViolationException(
            "duplicate", new RuntimeException("violates unique constraint \"uq_cash_sessions_operation_date\"")));

        assertThatThrownBy(() -> cashSessionService.openSession(openOn(StoreFormat.today(), null), "cajero1"))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessage("Ya existe una caja de ese día.");
    }

    @Test
    @DisplayName("CAJ-12: abrir toma el bloqueo de apertura antes de leer nada")
    void openSession_takesTheOpeningLockFirst() {
        stubOpeningGoesThrough();

        cashSessionService.openSession(openOn(StoreFormat.today(), null), "cajero1");

        var order = inOrder(cashSessionRepository);
        order.verify(cashSessionRepository).lockCashOpening();
        order.verify(cashSessionRepository).existsByStatus(CashSessionStatus.ABIERTA);
    }

    // --- CAJ-13: sequence and days without attention ---

    @Test
    @DisplayName("CAJ-13: elegir el 31/10 no pregunta nada, porque no queda ningún día atrás")
    void openSession_nextDay_asksNothing() {
        stubLastSession(lastSession(148, FRI_30));
        stubOpeningGoesThrough();

        CashSessionSummaryResponse response = cashSessionService.openSession(openOn(SAT_31, "Carga atrasada"), "cajero1");

        assertThat(response.operationDate()).isEqualTo(SAT_31);
        verify(cashNoAttentionDayRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("CAJ-13: abrir el 3/11 sin confirmar se rechaza con los días que faltan")
    void openSession_missingDaysNotConfirmed_rejected() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(openOn(TUE_3, "Carga atrasada"), "Falta resolver: 31/10, 2/11.");
    }

    @Test
    @DisplayName("CAJ-13: confirmar otros días que los que faltan se rechaza, para no marcar lo que no se vio")
    void openSession_otherDaysConfirmed_rejected() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(new OpenCashRequest(new BigDecimal("150.00"), "Carga", TUE_3, List.of(SAT_31), "Feriado"),
            "Falta resolver: 31/10, 2/11.");
    }

    @Test
    @DisplayName("CAJ-13: confirmar días que ya no faltan se rechaza")
    void openSession_daysNoLongerMissing_rejected() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(new OpenCashRequest(new BigDecimal("150.00"), "Carga", SAT_31, List.of(SAT_31), "Feriado"),
            "Esos días ya no faltan; vuelve a abrir el formulario.");
    }

    @Test
    @DisplayName("CAJ-13: confirmar sin comentario se rechaza con su propio mensaje")
    void openSession_confirmedWithoutComment_rejected() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(new OpenCashRequest(new BigDecimal("150.00"), "Carga", TUE_3, List.of(SAT_31, MON_2), "  "),
            "Confirmar los días sin atención pide un comentario.");
    }

    @Test
    @DisplayName("CAJ-13: el comentario de los días no sirve de comentario de apertura: son dos cosas")
    void openSession_noAttentionCommentDoesNotExplainThePastDay() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(new OpenCashRequest(new BigDecimal("150.00"), null, TUE_3, List.of(SAT_31, MON_2), "Feriado"),
            "Elegir un día anterior pide un comentario.");
    }

    @Test
    @DisplayName("CAJ-13: al confirmar, cada día queda marcado con el comentario, quién y cuándo, atado a la caja")
    void openSession_confirmed_marksTheDays() {
        stubLastSession(lastSession(148, FRI_30));
        stubOpeningGoesThrough();

        CashSessionSummaryResponse response = cashSessionService.openSession(new OpenCashRequest(new BigDecimal("150.00"),
            "Carga atrasada", TUE_3, List.of(MON_2, SAT_31), " Feriado "), "cajero1");

        assertThat(response.operationDate()).isEqualTo(TUE_3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CashNoAttentionDay>> marks = ArgumentCaptor.forClass(List.class);
        verify(cashNoAttentionDayRepository).saveAll(marks.capture());
        assertThat(marks.getValue()).extracting(CashNoAttentionDay::getNoAttentionDate).containsExactly(SAT_31, MON_2);
        assertThat(marks.getValue()).allSatisfy(mark -> {
            assertThat(mark.getComment()).isEqualTo("Feriado");
            assertThat(mark.getMarkedBy()).isEqualTo("cajero1");
            assertThat(mark.getMarkedAt()).isEqualTo(response.openedAt());
            assertThat(mark.getCashSessionId()).isEqualTo(sessionId);
        });
    }

    @Test
    @DisplayName("CAJ-13: un día vacío en los días sin atención se rechaza antes de leer o escribir nada")
    void openSession_emptyNoAttentionDay_isRejected() {
        assertThatThrownBy(() -> cashSessionService.openSession(new OpenCashRequest(new BigDecimal("150.00"),
                "Carga atrasada", TUE_3, Arrays.asList(null, MON_2), "Feriado"), "cajero1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Hay un día sin atención vacío; vuelve a abrir el formulario.");

        verifyNoInteractions(cashSessionRepository, cashNoAttentionDayRepository);
    }

    @Test
    @DisplayName("CAJ-13: se abre un domingo como cualquier otra caja")
    void openSession_sunday_opens() {
        stubLastSession(lastSession(148, SAT_31));
        stubOpeningGoesThrough();

        assertThat(cashSessionService.openSession(openOn(SUN_1, "Domingo"), "cajero1").operationDate()).isEqualTo(SUN_1);
    }

    @Test
    @DisplayName("CAJ-13: al abrir un domingo, el sábado que falta se resuelve igual")
    void openSession_sunday_resolvesTheSaturday() {
        stubLastSession(lastSession(148, FRI_30));

        assertOpeningRejected(openOn(SUN_1, "Domingo"), "Falta resolver: 31/10.");
    }

    @Test
    @DisplayName("CAJ-13: el lunes se abre igual si el domingo no tuvo caja: no se pregunta ni se marca")
    void openSession_mondayAfterSundayWithoutSession_asksNothing() {
        stubLastSession(lastSession(148, SAT_31));
        stubOpeningGoesThrough();

        CashSessionSummaryResponse response = cashSessionService.openSession(openOn(MON_2, "Carga atrasada"), "cajero1");

        assertThat(response.operationDate()).isEqualTo(MON_2);
        verify(cashNoAttentionDayRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("CAJ-13: el lunes se abre igual si el domingo tuvo caja")
    void openSession_mondayAfterSundayWithSession_asksNothing() {
        stubLastSession(lastSession(148, SUN_1));
        stubOpeningGoesThrough();

        assertThat(cashSessionService.openSession(openOn(MON_2, "Carga atrasada"), "cajero1").operationDate()).isEqualTo(MON_2);
    }

    @Test
    @DisplayName("CAJ-13: un día ya marcado no vuelve a faltar")
    void openSession_markedDayIsNotMissing() {
        stubLastSession(lastSession(148, FRI_30));
        stubOpeningGoesThrough();
        when(cashNoAttentionDayRepository.findByNoAttentionDateBetweenOrderByNoAttentionDateAsc(FRI_30, MON_2))
            .thenReturn(List.of(CashNoAttentionDay.builder().noAttentionDate(SAT_31).build()));

        assertThat(cashSessionService.openSession(openOn(MON_2, "Carga atrasada"), "cajero1").operationDate()).isEqualTo(MON_2);
    }

    @Test
    @DisplayName("CAJ-13: sin ninguna caja que no sea histórica, la regla no pide días atrás")
    void openSession_onlyHistorical_asksNoDays() {
        CashSession historical = lastSession(90, FRI_30);
        historical.setHistoricalImportId(UUID.randomUUID());
        stubLastSession(historical);
        stubOpeningGoesThrough();

        assertThat(cashSessionService.openSession(openOn(TUE_3, "Carga"), "cajero1").operationDate()).isEqualTo(TUE_3);
    }

    @Test
    @DisplayName("CAJ-13: sin ninguna caja, cualquier día que no sea futuro se abre sin preguntar")
    void openSession_noSessions_asksNoDays() {
        stubOpeningGoesThrough();

        assertThat(cashSessionService.openSession(openOn(TUE_3, "Carga"), "cajero1").operationDate()).isEqualTo(TUE_3);
    }

    // --- CAJ-12, CAJ-13: the proposal ---

    @Test
    @DisplayName("CAJ-12, CAJ-13: la propuesta trae hoy, el primer día posible y los días sin resolver hasta ayer")
    void getOpeningProposal_daysFromTheLastSession() {
        LocalDate today = StoreFormat.today();
        LocalDate lastDay = today.minusDays(8);
        stubLastSession(lastSession(148, lastDay));

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.today()).isEqualTo(today);
        assertThat(proposal.minDay()).isEqualTo(lastDay.plusDays(1));
        assertThat(proposal.lastSessionNumber()).isEqualTo(148L);
        assertThat(proposal.lastSessionDay()).isEqualTo(lastDay);
        assertThat(proposal.todayClosedSessionNumber()).isNull();
        assertThat(proposal.unresolvedDays())
            .isNotEmpty()
            .isEqualTo(lastDay.plusDays(1).datesUntil(today)
                .filter(day -> day.getDayOfWeek() != java.time.DayOfWeek.SUNDAY).toList());
    }

    @Test
    @DisplayName("CAJ-12: la propuesta avisa que la caja de hoy ya se cerró")
    void getOpeningProposal_todayClosed() {
        LocalDate today = StoreFormat.today();
        stubLastSession(lastSession(151, today));

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.todayClosedSessionNumber()).isEqualTo(151L);
        assertThat(proposal.minDay()).isEqualTo(today.plusDays(1));
        assertThat(proposal.unresolvedDays()).isEmpty();
    }

    @Test
    @DisplayName("CAJ-12: una caja histórica de hoy no se puede reabrir, así que la propuesta no la ofrece")
    void getOpeningProposal_todayHistorical_notOffered() {
        CashSession historical = lastSession(90, StoreFormat.today());
        historical.setHistoricalImportId(UUID.randomUUID());
        stubLastSession(historical);

        assertThat(cashSessionService.getOpeningProposal().todayClosedSessionNumber()).isNull();
    }

    @Test
    @DisplayName("CAJ-13: sin cajas no hay límite ni días por resolver")
    void getOpeningProposal_noSessions() {
        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.today()).isEqualTo(StoreFormat.today());
        assertThat(proposal.minDay()).isNull();
        assertThat(proposal.lastSessionNumber()).isNull();
        assertThat(proposal.unresolvedDays()).isEmpty();
    }

    @Test
    @DisplayName("CAJ-13: con solo históricas tampoco hay días por resolver, pero sí el límite")
    void getOpeningProposal_onlyHistorical() {
        LocalDate lastDay = StoreFormat.today().minusDays(8);
        CashSession historical = lastSession(90, lastDay);
        historical.setHistoricalImportId(UUID.randomUUID());
        stubLastSession(historical);

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.minDay()).isEqualTo(lastDay.plusDays(1));
        assertThat(proposal.unresolvedDays()).isEmpty();
    }

    // --- CAJ-14: reopening ---

    private CashSession closedWithDifference(String difference) {
        CashSession closed = lastSession(150, FRI_30);
        closed.setId(sessionId);
        closed.setClosingComment("Cierre");
        closed.setClosedBy("ana");
        closed.setDifference(difference != null ? new BigDecimal(difference) : null);
        return closed;
    }

    private void stubReopenable(CashSession closed) {
        when(cashSessionRepository.findById(sessionId)).thenReturn(Optional.of(closed));
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        when(cashSessionRepository.findFirstByHistoricalImportIdIsNullOrderBySessionNumberDesc()).thenReturn(Optional.of(closed));
        lenient().when(cashSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(closed));
        lenient().when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cashSessionReopeningRepository.saveAndFlush(any(CashSessionReopening.class))).thenAnswer(invocation -> {
            CashSessionReopening reopening = invocation.getArgument(0);
            return CashSessionReopening.builder().id(UUID.randomUUID()).cashSessionId(reopening.getCashSessionId())
                .voidedAdjustmentId(reopening.getVoidedAdjustmentId()).build();
        });
    }

    private FinancialTransaction stubClosingAdjustment(CashSession closed, FinancialTransactionType type, String amount, String description) {
        FinancialTransaction adjustment = FinancialTransaction.builder()
            .id(UUID.randomUUID())
            .type(type)
            .amount(new BigDecimal(amount))
            .description(description)
            .status(FinancialTransactionStatus.ACTIVO)
            .cashSessionId(closed.getId())
            .build();
        when(financialTransactionRepository.findActiveClosingAdjustments(closed.getId(),
                List.of("Sobrante al cierre de caja #150", "Faltante al cierre de caja #150")))
            .thenReturn(List.of(adjustment));
        lenient().when(financialTransactionRepository.findByIdForUpdate(adjustment.getId())).thenReturn(Optional.of(adjustment));
        return adjustment;
    }

    private void assertReopenRejectedUntouched(Class<? extends Throwable> type, String message, CashSession closed) {
        BigDecimal closing = closed.getClosingAmount();
        assertThatThrownBy(() -> cashSessionService.reopenSession(sessionId, new ReopenCashRequest("Error"), "ana"))
            .isInstanceOf(type)
            .hasMessage(message);
        assertThat(closed.getStatus()).isEqualTo(CashSessionStatus.CERRADA);
        assertThat(closed.getClosingAmount()).isEqualTo(closing);
        verify(financialAccountRepository, never()).save(any());
        verify(financialTransactionRepository, never()).save(any());
        verify(cashSessionReopeningRepository, never()).saveAndFlush(any());
        verify(cashDenominationCutRepository, never()).moveToReopening(any(), any());
    }

    @Test
    @DisplayName("CAJ-14: sin motivo se rechaza, sin cuerpo, vacío o en blanco, y sin tomar el bloqueo")
    void reopenSession_withoutReason_rejected() {
        for (ReopenCashRequest request : Arrays.asList(null, new ReopenCashRequest(null), new ReopenCashRequest("   "))) {
            assertThatThrownBy(() -> cashSessionService.reopenSession(sessionId, request, "ana"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El motivo es obligatorio.");
        }
        verify(cashSessionRepository, never()).lockCashOpening();
    }

    @Test
    @DisplayName("CAJ-14: una caja que no existe responde no encontrada")
    void reopenSession_notFound() {
        when(cashSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashSessionService.reopenSession(sessionId, new ReopenCashRequest("Error"), "ana"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("CAJ-14: una caja histórica no se reabre")
    void reopenSession_historical_rejected() {
        CashSession closed = closedWithDifference("0.00");
        closed.setHistoricalImportId(UUID.randomUUID());
        when(cashSessionRepository.findById(sessionId)).thenReturn(Optional.of(closed));

        assertReopenRejectedUntouched(IllegalStateException.class, "Una caja histórica no se reabre.", closed);
    }

    @Test
    @DisplayName("CAJ-14: con una caja abierta se rechaza")
    void reopenSession_withOpenSession_rejected() {
        CashSession closed = closedWithDifference("0.00");
        when(cashSessionRepository.findById(sessionId)).thenReturn(Optional.of(closed));
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(true);

        assertReopenRejectedUntouched(IllegalStateException.class, "Ya hay una caja abierta; ciérrala antes de reabrir otra.", closed);
    }

    @Test
    @DisplayName("CAJ-14: con una caja posterior, aunque esté cerrada, se rechaza")
    void reopenSession_notTheLast_rejected() {
        CashSession closed = closedWithDifference("0.00");
        when(cashSessionRepository.findById(sessionId)).thenReturn(Optional.of(closed));
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        when(cashSessionRepository.findFirstByHistoricalImportIdIsNullOrderBySessionNumberDesc())
            .thenReturn(Optional.of(lastSession(151, SAT_31)));

        assertReopenRejectedUntouched(IllegalStateException.class, "Solo se puede reabrir la última caja.", closed);
    }

    @Test
    @DisplayName("CAJ-14: reabrir con faltante devuelve el saldo de Caja, anula el ajuste y guarda lo que tenía el cierre")
    void reopenSession_withShortage_revertsBalance() {
        CashSession closed = closedWithDifference("-7.00");
        stubReopenable(closed);
        FinancialTransaction adjustment = stubClosingAdjustment(closed, FinancialTransactionType.GASTO_MANUAL, "7.00", "Faltante al cierre de caja #150");
        FinancialAccount cash = stubCashAccount("150.00");
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(4L);

        CashSessionSummaryResponse response = cashSessionService.reopenSession(sessionId, new ReopenCashRequest(" Cerrada por error "), "luis");

        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("157.00");
        assertThat(cash.getCurrentBalance().scale()).isEqualTo(2);
        assertThat(adjustment.getStatus()).isEqualTo(FinancialTransactionStatus.ANULADO);
        assertThat(adjustment.getVoidedBy()).isEqualTo("luis");
        assertThat(adjustment.getVoidedAt()).isNotNull();
        assertThat(adjustment.getVoidReason()).isEqualTo("Reapertura de caja #150: Cerrada por error");

        ArgumentCaptor<CashSessionReopening> reopening = ArgumentCaptor.forClass(CashSessionReopening.class);
        verify(cashSessionReopeningRepository).saveAndFlush(reopening.capture());
        assertThat(reopening.getValue().getCashSessionId()).isEqualTo(sessionId);
        assertThat(reopening.getValue().getReason()).isEqualTo("Cerrada por error");
        assertThat(reopening.getValue().getReopenedBy()).isEqualTo("luis");
        assertThat(reopening.getValue().getReopenedAt()).isNotNull();
        assertThat(reopening.getValue().getClosingAmount()).isEqualByComparingTo("150.00");
        assertThat(reopening.getValue().getClosingComment()).isEqualTo("Cierre");
        assertThat(reopening.getValue().getClosedBy()).isEqualTo("ana");
        assertThat(reopening.getValue().getClosedAt()).isNotNull();
        assertThat(reopening.getValue().getExpectedCash()).isEqualByComparingTo("150.00");
        assertThat(reopening.getValue().getDifference()).isEqualByComparingTo("-7.00");
        assertThat(reopening.getValue().getVoidedAdjustmentId()).isEqualTo(adjustment.getId());
        verify(cashDenominationCutRepository).moveToReopening(eq(sessionId), any());

        assertThat(response.status()).isEqualTo(CashSessionStatus.ABIERTA);
        assertThat(response.sessionNumber()).isEqualTo(150L);
        assertThat(response.operationDate()).isEqualTo(FRI_30);
        assertThat(response.openingAmount()).isEqualByComparingTo("100.00");
        assertThat(response.salesCount()).isEqualTo(4L);
        assertThat(response.closingAmount()).isNull();
        assertThat(response.closingComment()).isNull();
        assertThat(response.closedBy()).isNull();
        assertThat(response.closedAt()).isNull();
        assertThat(response.difference()).isNull();
    }

    @Test
    @DisplayName("CAJ-14: reabrir con sobrante baja el saldo de Caja a lo que era")
    void reopenSession_withSurplus_revertsBalance() {
        CashSession closed = closedWithDifference("12.50");
        stubReopenable(closed);
        FinancialTransaction adjustment = stubClosingAdjustment(closed, FinancialTransactionType.INGRESO_MANUAL, "12.50", "Sobrante al cierre de caja #150");
        FinancialAccount cash = stubCashAccount("150.00");

        cashSessionService.reopenSession(sessionId, new ReopenCashRequest("Error"), "luis");

        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("137.50");
        assertThat(adjustment.getStatus()).isEqualTo(FinancialTransactionStatus.ANULADO);
    }

    @Test
    @DisplayName("CAJ-14: la reversión puede dejar Caja en negativo, porque vuelve a lo que era")
    void reopenSession_surplusBackToNegative_allowed() {
        CashSession closed = closedWithDifference("30.00");
        stubReopenable(closed);
        stubClosingAdjustment(closed, FinancialTransactionType.INGRESO_MANUAL, "30.00", "Sobrante al cierre de caja #150");
        FinancialAccount cash = stubCashAccount("20.00");

        cashSessionService.reopenSession(sessionId, new ReopenCashRequest("Error"), "luis");

        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("-10.00");
    }

    @Test
    @DisplayName("CAJ-14: sin diferencia no toca ningún saldo ni bloquea la cuenta")
    void reopenSession_withoutDifference_touchesNoBalance() {
        CashSession closed = closedWithDifference("0.00");
        stubReopenable(closed);

        CashSessionSummaryResponse response = cashSessionService.reopenSession(sessionId, new ReopenCashRequest("Error"), "luis");

        assertThat(response.status()).isEqualTo(CashSessionStatus.ABIERTA);
        verify(financialAccountRepository, never()).findAllByIdForUpdate(any());
        verify(financialAccountRepository, never()).save(any());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-14: el motivo largo se corta en la anulación; entero queda en la reapertura")
    void reopenSession_longReason_cutInTheVoid() {
        CashSession closed = closedWithDifference("-7.00");
        stubReopenable(closed);
        FinancialTransaction adjustment = stubClosingAdjustment(closed, FinancialTransactionType.GASTO_MANUAL, "7.00", "Faltante al cierre de caja #150");
        stubCashAccount("150.00");
        String reason = "x".repeat(500);

        cashSessionService.reopenSession(sessionId, new ReopenCashRequest(reason), "luis");

        assertThat(adjustment.getVoidReason()).hasSize(500);
        ArgumentCaptor<CashSessionReopening> reopening = ArgumentCaptor.forClass(CashSessionReopening.class);
        verify(cashSessionReopeningRepository).saveAndFlush(reopening.capture());
        assertThat(reopening.getValue().getReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("CAJ-14, T-14: el orden de bloqueos es apertura, ajuste, caja, cuenta")
    void reopenSession_lockOrder() {
        CashSession closed = closedWithDifference("-7.00");
        stubReopenable(closed);
        FinancialTransaction adjustment = stubClosingAdjustment(closed, FinancialTransactionType.GASTO_MANUAL, "7.00", "Faltante al cierre de caja #150");
        FinancialAccount cash = stubCashAccount("150.00");

        cashSessionService.reopenSession(sessionId, new ReopenCashRequest("Error"), "luis");

        var order = inOrder(cashSessionRepository, financialTransactionRepository, financialAccountRepository);
        order.verify(cashSessionRepository).lockCashOpening();
        order.verify(cashSessionRepository).findById(sessionId);
        order.verify(financialTransactionRepository).findByIdForUpdate(adjustment.getId());
        order.verify(cashSessionRepository).findByIdForUpdate(sessionId);
        order.verify(financialAccountRepository).findAllByIdForUpdate(List.of(cash.getId()));
    }

    @Test
    @DisplayName("CAJ-14: con diferencia y sin ajuste se rechaza sin cambiar nada")
    void reopenSession_differenceWithoutAdjustment_rejected() {
        CashSession closed = closedWithDifference("-7.00");
        stubReopenable(closed);

        assertReopenRejectedUntouched(IllegalStateException.class,
            "El ajuste del cierre de la caja #150 no coincide con su diferencia; no se puede reabrir.", closed);
    }

    @Test
    @DisplayName("CAJ-14: si el monto del ajuste no calza se rechaza sin cambiar nada")
    void reopenSession_adjustmentAmountMismatch_rejected() {
        CashSession closed = closedWithDifference("-7.00");
        stubReopenable(closed);
        stubClosingAdjustment(closed, FinancialTransactionType.GASTO_MANUAL, "8.00", "Faltante al cierre de caja #150");

        assertReopenRejectedUntouched(IllegalStateException.class,
            "El ajuste del cierre de la caja #150 no coincide con su diferencia; no se puede reabrir.", closed);
    }

    @Test
    @DisplayName("CAJ-14: si el tipo del ajuste no calza se rechaza sin cambiar nada")
    void reopenSession_adjustmentTypeMismatch_rejected() {
        CashSession closed = closedWithDifference("-7.00");
        stubReopenable(closed);
        stubClosingAdjustment(closed, FinancialTransactionType.INGRESO_MANUAL, "7.00", "Faltante al cierre de caja #150");

        assertReopenRejectedUntouched(IllegalStateException.class,
            "El ajuste del cierre de la caja #150 no coincide con su diferencia; no se puede reabrir.", closed);
    }

    @Test
    @DisplayName("CAJ-14: sin diferencia pero con un ajuste de cierre activo se rechaza sin cambiar nada")
    void reopenSession_adjustmentWithoutDifference_rejected() {
        CashSession closed = closedWithDifference("0.00");
        stubReopenable(closed);
        stubClosingAdjustment(closed, FinancialTransactionType.GASTO_MANUAL, "7.00", "Faltante al cierre de caja #150");

        assertReopenRejectedUntouched(IllegalStateException.class,
            "El ajuste del cierre de la caja #150 no coincide con su diferencia; no se puede reabrir.", closed);
    }

    @Test
    @DisplayName("CAJ-14: el historial ofrece reabrir solo la última caja cerrada que no es histórica")
    void getAllSessions_onlyTheLastIsReopenable() {
        CashSession last = lastSession(150, SAT_31);
        last.setId(UUID.randomUUID());
        CashSession previous = lastSession(149, FRI_30);
        previous.setId(UUID.randomUUID());
        CashSession historical = lastSession(90, LocalDate.of(2020, 9, 1));
        historical.setId(UUID.randomUUID());
        historical.setHistoricalImportId(UUID.randomUUID());
        when(cashSessionRepository.findAllByOrderByOpenedAtDesc()).thenReturn(List.of(last, previous, historical));
        when(cashSessionRepository.findFirstByHistoricalImportIdIsNullOrderBySessionNumberDesc()).thenReturn(Optional.of(last));

        assertThat(cashSessionService.getAllSessions()).extracting(CashSessionSummaryResponse::reopenable)
            .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("CAJ-14: con una caja abierta ninguna se ofrece para reabrir")
    void getAllSessions_withOpenSession_noneReopenable() {
        CashSession closed = lastSession(150, SAT_31);
        closed.setId(UUID.randomUUID());
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(true);
        when(cashSessionRepository.findAllByOrderByOpenedAtDesc()).thenReturn(List.of(activeSession, closed));

        assertThat(cashSessionService.getAllSessions()).extracting(CashSessionSummaryResponse::reopenable)
            .containsExactly(false, false);
    }

    private FinancialAccount stubCashAccount(String balance) {
        FinancialAccount cash = FinancialAccount.builder()
            .id(UUID.randomUUID())
            .name("Caja")
            .isCashAccount(true)
            .currentBalance(new BigDecimal(balance))
            .build();
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(cash));
        lenient().when(financialAccountRepository.findAllByIdForUpdate(List.of(cash.getId()))).thenReturn(List.of(cash));
        return cash;
    }

    private FinancialAccount stubAdjustment(FinancialCategorySystemKey key, FinancialCategoryType type, String cashBalance) {
        FinancialAccount cash = FinancialAccount.builder()
            .id(UUID.randomUUID())
            .name("Caja")
            .isCashAccount(true)
            .currentBalance(new BigDecimal(cashBalance))
            .build();
        FinancialCategory category = FinancialCategory.builder()
            .id(UUID.randomUUID())
            .name("Ajuste")
            .type(type)
            .systemKey(key)
            .build();
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(key)).thenReturn(Optional.of(category));
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(cash));
        when(financialAccountRepository.findAllByIdForUpdate(List.of(cash.getId()))).thenReturn(List.of(cash));
        return cash;
    }

    private FinancialTransaction captureAdjustment() {
        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
