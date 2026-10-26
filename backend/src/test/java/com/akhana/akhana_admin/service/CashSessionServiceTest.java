package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.CashSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
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

    private CashSessionServiceImpl cashSessionService;

    private UUID sessionId;
    private CashSession activeSession;

    @BeforeEach
    void setUp() {
        cashSessionService = new CashSessionServiceImpl(cashSessionRepository, saleRepository,
            new ExpectedCashCalculator(financialTransactionRepository),
            financialAccountRepository, financialCategoryRepository, financialTransactionRepository);
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
        when(cashSessionRepository.findFirstByStatusOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.5")));

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.proposedAmount()).isEqualByComparingTo("245.50");
        assertThat(proposal.previousSessionNumber()).isEqualTo(7L);
    }

    @Test
    @DisplayName("CAJ-10: sin caja anterior ni cuenta Caja propone 0.00")
    void getOpeningProposal_noPreviousSession_returnsZero() {
        when(cashSessionRepository.findFirstByStatusOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.empty());

        CashOpeningProposalResponse proposal = cashSessionService.getOpeningProposal();

        assertThat(proposal.proposedAmount()).isEqualByComparingTo("0.00");
        assertThat(proposal.previousSessionNumber()).isNull();
    }

    @Test
    @DisplayName("FIN-01: la primera caja propone el saldo al corte de Caja")
    void getOpeningProposal_noPreviousSession_returnsCashBalance() {
        when(cashSessionRepository.findFirstByStatusOrderByClosedAtDesc(CashSessionStatus.CERRADA))
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
        when(cashSessionRepository.findFirstByStatusOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.50")));
        when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashSessionSummaryResponse response = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal("245.5"), "  "), "cajero1");

        assertThat(response.openingAmount()).isEqualByComparingTo("245.50");
        assertThat(response.openingComment()).isNull();
    }

    @Test
    @DisplayName("CAJ-10: abrir con un monto distinto al cierre anterior sin comentario se rechaza")
    void openSession_changedAmountWithoutComment_throws() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        when(cashSessionRepository.findFirstByStatusOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.50")));

        assertThatThrownBy(() -> cashSessionService.openSession(
                new OpenCashRequest(new BigDecimal("200.00"), " "), "cajero1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("245.50")
            .hasMessageContaining("comentario de apertura");
        verify(cashSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("CAJ-10: abrir con un monto distinto al cierre anterior y comentario se acepta")
    void openSession_changedAmountWithComment_succeeds() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.ABIERTA)).thenReturn(false);
        when(cashSessionRepository.findFirstByStatusOrderByClosedAtDesc(CashSessionStatus.CERRADA))
            .thenReturn(Optional.of(closedSession("245.50")));
        when(cashSessionRepository.saveAndFlush(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashSessionSummaryResponse response = cashSessionService.openSession(
            new OpenCashRequest(new BigDecimal("200.00"), " Se depositaron 45.50 "), "cajero1");

        assertThat(response.openingAmount()).isEqualByComparingTo("200.00");
        assertThat(response.openingComment()).isEqualTo("Se depositaron 45.50");
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
        CloseCashRequest request = new CloseCashRequest(new BigDecimal("120.00"), "Cierre de turno con sobrante", cuts);
        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(request, "cajero1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(CashSessionStatus.CERRADA);
        assertThat(response.closingAmount()).isEqualByComparingTo("120.00");
        assertThat(response.difference()).isEqualByComparingTo("20.00");
        assertThat(response.closedBy()).isEqualTo("cajero1");
        assertThat(activeSession.getCuts()).hasSize(2);
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
            Collections.emptyList()
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
            new CloseCashRequest(new BigDecimal("680.00"), null, Collections.emptyList()), "admin");

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
            new CloseCashRequest(new BigDecimal("112.50"), "Sobró sencillo", Collections.emptyList()), "cajero1");

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
    @DisplayName("CAJ-09: un faltante se registra como gasto de Ajuste en Caja y baja su saldo")
    void closeSession_withShortage_recordsExpenseAdjustment() {
        FinancialAccount cash = stubAdjustment(FinancialCategorySystemKey.CASH_SHORTAGE, FinancialCategoryType.GASTO, "100.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(
            new CloseCashRequest(new BigDecimal("93.00"), null, Collections.emptyList()), "cajero1");

        assertThat(response.difference()).isEqualByComparingTo("-7.00");
        FinancialTransaction tx = captureAdjustment();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.GASTO_MANUAL);
        assertThat(tx.getAmount()).isEqualByComparingTo("7.00");
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
            new CloseCashRequest(new BigDecimal("100.00"), null, Collections.emptyList()), "cajero1");

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
            new CloseCashRequest(new BigDecimal("120.00"), null, Collections.emptyList()), "cajero1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Ajuste");
        verify(financialAccountRepository, never()).save(any());
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
