package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.CashSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private CashSessionServiceImpl cashSessionService;

    private UUID sessionId;
    private CashSession activeSession;

    @BeforeEach
    void setUp() {
        cashSessionService = new CashSessionServiceImpl(cashSessionRepository, saleRepository,
            new ExpectedCashCalculator(financialTransactionRepository));
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
}
