package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.CashSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private CashSessionServiceImpl cashSessionService;

    private UUID sessionId;
    private CashSession activeSession;

    @BeforeEach
    void setUp() {
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
        when(cashSessionRepository.save(any(CashSession.class))).thenAnswer(invocation -> {
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
        verify(cashSessionRepository, never()).save(any());
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
    @DisplayName("Debe registrar venta en EFECTIVO incrementando totalSales y expectedCash")
    void registerSale_cash_updatesExpectedCash() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(0L);
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SaleRequest request = new SaleRequest(new BigDecimal("50.00"), PaymentMethod.EFECTIVO, "Venta productos");
        SaleResponse response = cashSessionService.registerSaleInCurrentSession(request, "vendedor1");

        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.EFECTIVO);

        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("50.00");
        assertThat(activeSession.getTotalSales()).isEqualByComparingTo("50.00");
        assertThat(activeSession.getExpectedCash()).isEqualByComparingTo("150.00"); // 100 inicial + 50 efectivo
        assertThat(activeSession.getTotalSalesQr()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Debe registrar venta por QR incrementando totalSalesQr y totalSales, pero NO expectedCash")
    void registerSale_qr_doesNotUpdateExpectedCash() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(saleRepository.countByCashSessionId(sessionId)).thenReturn(0L);
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SaleRequest request = new SaleRequest(new BigDecimal("80.00"), PaymentMethod.QR, "Venta té matcha QR");
        SaleResponse response = cashSessionService.registerSaleInCurrentSession(request, "vendedor1");

        assertThat(response).isNotNull();
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.QR);

        assertThat(activeSession.getTotalSalesQr()).isEqualByComparingTo("80.00");
        assertThat(activeSession.getTotalSales()).isEqualByComparingTo("80.00");
        assertThat(activeSession.getExpectedCash()).isEqualByComparingTo("100.00"); // Permanece 100 inicial
        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Debe cerrar caja y calcular la diferencia frente al efectivo esperado")
    void closeSession_withDifferenceAndCuts_success() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
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
}
