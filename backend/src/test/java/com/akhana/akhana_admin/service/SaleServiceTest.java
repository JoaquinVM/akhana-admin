package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.SaleServiceImpl;
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
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private CashSessionRepository cashSessionRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private SaleServiceImpl saleService;

    private CashSession activeSession;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        activeSession = CashSession.builder()
            .id(UUID.randomUUID())
            .sessionNumber(1L)
            .status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00"))
            .expectedCash(new BigDecimal("100.00"))
            .totalSalesCash(BigDecimal.ZERO)
            .totalSalesQr(BigDecimal.ZERO)
            .totalSales(BigDecimal.ZERO)
            .build();

        product1 = Product.builder()
            .id(UUID.randomUUID())
            .code("PRD-001")
            .name("Café Premium")
            .sellPrice(new BigDecimal("20.00"))
            .status(ProductStatus.ACTIVO)
            .build();

        product2 = Product.builder()
            .id(UUID.randomUUID())
            .code("PRD-002")
            .name("Té Verde")
            .sellPrice(new BigDecimal("15.00"))
            .status(ProductStatus.ACTIVO)
            .build();
    }

    @Test
    @DisplayName("Debe registrar venta en efectivo calculando cambio y afectando saldos de caja")
    void shouldRegisterSaleWithCashAndCalculateChange() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(saleRepository.countByCashSessionId(activeSession.getId())).thenReturn(0L);
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SaleItemRequest itemReq = new SaleItemRequest(product1.getId(), 2, new BigDecimal("2.00")); // final 18 * 2 = 36
        SaleDetailRequest req = new SaleDetailRequest(
            List.of(itemReq),
            new BigDecimal("6.00"), // total = 30.00
            PaymentMethod.EFECTIVO,
            new BigDecimal("30.00"),
            BigDecimal.ZERO,
            new BigDecimal("50.00"), // 50 recibido -> 20 cambio
            "Venta de café con descuento"
        );

        SaleResponse response = saleService.registerSale(req, "cajero");

        assertThat(response).isNotNull();
        assertThat(response.subtotalAmount()).isEqualByComparingTo("40.00");
        assertThat(response.discountItemsTotal()).isEqualByComparingTo("4.00");
        assertThat(response.globalDiscountAmount()).isEqualByComparingTo("6.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("30.00");
        assertThat(response.amountReceived()).isEqualByComparingTo("50.00");
        assertThat(response.changeGiven()).isEqualByComparingTo("20.00");
        assertThat(response.status()).isEqualTo(SaleStatus.COMPLETADA);

        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("30.00");
        assertThat(activeSession.getExpectedCash()).isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("Debe registrar venta con pago mixto (Efectivo + QR) actualizando ambos saldos")
    void shouldRegisterSaleWithMixedPayment() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
        when(saleRepository.countByCashSessionId(activeSession.getId())).thenReturn(1L);
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SaleItemRequest item1 = new SaleItemRequest(product1.getId(), 2, BigDecimal.ZERO); // 40
        SaleItemRequest item2 = new SaleItemRequest(product2.getId(), 4, BigDecimal.ZERO); // 60 -> total 100
        SaleDetailRequest req = new SaleDetailRequest(
            List.of(item1, item2),
            BigDecimal.ZERO,
            PaymentMethod.MIXTO,
            new BigDecimal("40.00"), // 40 efectivo
            new BigDecimal("60.00"), // 60 QR
            new BigDecimal("50.00"), // 50 recibido en efectivo -> 10 cambio
            "Pago mixto"
        );

        SaleResponse response = saleService.registerSale(req, "cajero");

        assertThat(response.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(response.amountCash()).isEqualByComparingTo("40.00");
        assertThat(response.amountQr()).isEqualByComparingTo("60.00");
        assertThat(response.changeGiven()).isEqualByComparingTo("10.00");

        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("40.00");
        assertThat(activeSession.getTotalSalesQr()).isEqualByComparingTo("60.00");
        assertThat(activeSession.getExpectedCash()).isEqualByComparingTo("140.00");
    }

    @Test
    @DisplayName("Debe rechazar venta cuando no existe caja abierta")
    void shouldRejectSaleWhenNoCashSessionOpen() {
        when(cashSessionRepository.findByStatus(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        SaleDetailRequest req = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), 1, BigDecimal.ZERO)),
            BigDecimal.ZERO,
            PaymentMethod.EFECTIVO,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            null
        );

        assertThatThrownBy(() -> saleService.registerSale(req, "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Para registrar una venta debe existir una caja abierta.");
    }

    @Test
    @DisplayName("Debe anular venta exitosamente en caja abierta y revertir acumuladores")
    void shouldVoidSaleInOpenSessionAndRevertTotals() {
        activeSession.setTotalSalesCash(new BigDecimal("50.00"));
        activeSession.setTotalSalesQr(new BigDecimal("30.00"));
        activeSession.setTotalSales(new BigDecimal("80.00"));
        activeSession.setExpectedCash(new BigDecimal("150.00"));

        Sale sale = Sale.builder()
            .id(UUID.randomUUID())
            .saleNumber("VTA-0001-001")
            .cashSession(activeSession)
            .status(SaleStatus.COMPLETADA)
            .totalAmount(new BigDecimal("80.00"))
            .paymentMethod(PaymentMethod.MIXTO)
            .amountCash(new BigDecimal("50.00"))
            .amountQr(new BigDecimal("30.00"))
            .items(new ArrayList<>())
            .build();

        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(saleRepository.save(any(Sale.class))).thenReturn(sale);

        VoidSaleRequest voidReq = new VoidSaleRequest("Error de digitación en productos");
        SaleResponse response = saleService.voidSale(sale.getId(), voidReq, "supervisor");

        assertThat(response.status()).isEqualTo(SaleStatus.ANULADA);
        assertThat(response.voidReason()).isEqualTo("Error de digitación en productos");
        assertThat(response.voidedBy()).isEqualTo("supervisor");

        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("0.00");
        assertThat(activeSession.getTotalSalesQr()).isEqualByComparingTo("0.00");
        assertThat(activeSession.getTotalSales()).isEqualByComparingTo("0.00");
        assertThat(activeSession.getExpectedCash()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Debe rechazar anulación si la caja ya fue cerrada")
    void shouldRejectVoidWhenSessionIsClosed() {
        CashSession closedSession = CashSession.builder()
            .id(UUID.randomUUID())
            .status(CashSessionStatus.CERRADA)
            .build();

        Sale sale = Sale.builder()
            .id(UUID.randomUUID())
            .cashSession(closedSession)
            .status(SaleStatus.COMPLETADA)
            .build();

        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        VoidSaleRequest voidReq = new VoidSaleRequest("Intento fuera de turno");
        assertThatThrownBy(() -> saleService.voidSale(sale.getId(), voidReq, "supervisor"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Solo se pueden anular ventas pertenecientes a una caja abierta.");
    }
}
