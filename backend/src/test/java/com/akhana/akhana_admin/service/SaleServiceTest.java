package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.SaleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private CashSessionRepository cashSessionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialCategoryRepository financialCategoryRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    private SaleServiceImpl saleService;

    private CashSession activeSession;
    private Product product1;
    private Product product2;
    private FinancialAccount cashAcc;
    private FinancialAccount qrAcc;
    private FinancialCategory salesCat;

    @BeforeEach
    void setUp() {
        saleService = new SaleServiceImpl(saleRepository, cashSessionRepository, productRepository, financialAccountRepository,
            financialCategoryRepository, financialTransactionRepository, new ExpectedCashCalculator(financialTransactionRepository));
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

        cashAcc = FinancialAccount.builder()
            .id(UUID.randomUUID())
            .name("Caja")
            .currentBalance(new BigDecimal("100.00"))
            .isCashAccount(true)
            .build();

        qrAcc = FinancialAccount.builder()
            .id(UUID.randomUUID())
            .name("BISA")
            .currentBalance(new BigDecimal("200.00"))
            .isQrAccount(true)
            .build();

        salesCat = FinancialCategory.builder()
            .id(UUID.randomUUID())
            .name("Ventas")
            .type(FinancialCategoryType.INGRESO)
            .isSalesDefault(true)
            .build();
    }

    private void stubSaleFinance(List<FinancialAccount> accounts, Optional<FinancialCategory> category) {
        when(financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()).thenReturn(category);
        lenient().when(financialAccountRepository.findSaleAccountsForUpdate()).thenReturn(accounts);
    }

    private SaleDetailRequest singleItemSale(PaymentMethod method) {
        return new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), 1, BigDecimal.ZERO)),
            BigDecimal.ZERO,
            method,
            method == PaymentMethod.EFECTIVO ? new BigDecimal("20.00") : BigDecimal.ZERO,
            method == PaymentMethod.QR ? new BigDecimal("20.00") : BigDecimal.ZERO,
            method == PaymentMethod.EFECTIVO ? new BigDecimal("20.00") : null,
            null
        );
    }

    @Test
    @DisplayName("Debe registrar venta en efectivo calculando cambio y afectando saldos de caja")
    void shouldRegisterSaleWithCashAndCalculateChange() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(saleRepository.nextSaleNumber()).thenReturn(1L);
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
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
        when(saleRepository.nextSaleNumber()).thenReturn(2L);
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
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

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

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
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

    @Test
    @DisplayName("Debe generar transacciones financieras al registrar venta mixta")
    void shouldGenerateFinancialIncomeTransactionsOnSale() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(saleRepository.nextSaleNumber()).thenReturn(1L);
        when(saleRepository.save(any(Sale.class))).thenAnswer(i -> {
            Sale s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));

        SaleDetailRequest req = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), 2, BigDecimal.ZERO)),
            BigDecimal.ZERO,
            PaymentMethod.MIXTO,
            new BigDecimal("20.00"),
            new BigDecimal("20.00"),
            new BigDecimal("20.00"),
            null
        );

        SaleResponse res = saleService.registerSale(req, "cajero");

        assertThat(res).isNotNull();
        assertThat(cashAcc.getCurrentBalance()).isEqualByComparingTo("120.00");
        assertThat(qrAcc.getCurrentBalance()).isEqualByComparingTo("220.00");
        verify(financialTransactionRepository, times(2)).save(any(FinancialTransaction.class));
    }

    @Test
    @DisplayName("Debe anular transacciones financieras asociadas al anular venta")
    void shouldVoidFinancialIncomeTransactionsOnSaleVoid() {
        cashAcc.setCurrentBalance(new BigDecimal("150.00"));

        Sale sale = Sale.builder()
            .id(UUID.randomUUID())
            .saleNumber("VTA-0001-002")
            .cashSession(activeSession)
            .status(SaleStatus.COMPLETADA)
            .totalAmount(new BigDecimal("50.00"))
            .paymentMethod(PaymentMethod.EFECTIVO)
            .amountCash(new BigDecimal("50.00"))
            .amountQr(BigDecimal.ZERO)
            .items(new ArrayList<>())
            .build();

        FinancialTransaction relatedTx = FinancialTransaction.builder()
            .id(UUID.randomUUID())
            .type(FinancialTransactionType.VENTA_INGRESO)
            .sourceAccount(cashAcc)
            .amount(new BigDecimal("50.00"))
            .status(FinancialTransactionStatus.ACTIVO)
            .saleId(sale.getId())
            .build();

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(saleRepository.save(any(Sale.class))).thenReturn(sale);
        when(financialTransactionRepository.findBySaleId(sale.getId())).thenReturn(List.of(relatedTx));

        VoidSaleRequest voidReq = new VoidSaleRequest("Anulación por error");
        saleService.voidSale(sale.getId(), voidReq, "supervisor");

        assertThat(relatedTx.getStatus()).isEqualTo(FinancialTransactionStatus.ANULADO);
        assertThat(relatedTx.getVoidReason()).isEqualTo("Anulación por error");
        assertThat(cashAcc.getCurrentBalance()).isEqualByComparingTo("100.00");
        verify(financialTransactionRepository).save(relatedTx);
        verify(financialAccountRepository).save(cashAcc);
    }

    @Test
    @DisplayName("T-17: rechaza la venta si no existe la categoría Venta y no guarda nada")
    void shouldRejectSaleWithoutSalesCategory() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.empty());

        assertThatThrownBy(() -> saleService.registerSale(singleItemSale(PaymentMethod.EFECTIVO), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("categoría Venta");

        verify(saleRepository, never()).save(any(Sale.class));
        verify(financialTransactionRepository, never()).save(any(FinancialTransaction.class));
        assertThat(activeSession.getTotalSales()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("T-17: rechaza la venta en efectivo si no existe la cuenta Caja")
    void shouldRejectCashSaleWithoutCashAccount() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        stubSaleFinance(List.of(qrAcc), Optional.of(salesCat));

        assertThatThrownBy(() -> saleService.registerSale(singleItemSale(PaymentMethod.EFECTIVO), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cuenta Caja");

        verify(saleRepository, never()).save(any(Sale.class));
        verify(cashSessionRepository, never()).save(any(CashSession.class));
    }

    @Test
    @DisplayName("T-17: rechaza la venta QR si no hay cuenta marcada para cobros QR")
    void shouldRejectQrSaleWithoutQrAccount() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        stubSaleFinance(List.of(cashAcc), Optional.of(salesCat));

        assertThatThrownBy(() -> saleService.registerSale(singleItemSale(PaymentMethod.QR), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cobros QR");

        verify(saleRepository, never()).save(any(Sale.class));
        assertThat(cashAcc.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("T-17: rechaza la venta mixta si falta la cuenta QR aunque exista Caja")
    void shouldRejectMixedSaleWithoutQrAccount() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        stubSaleFinance(List.of(cashAcc), Optional.of(salesCat));

        SaleDetailRequest req = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), 1, BigDecimal.ZERO)),
            BigDecimal.ZERO, PaymentMethod.MIXTO, new BigDecimal("5.00"), new BigDecimal("15.00"), new BigDecimal("5.00"), null);

        assertThatThrownBy(() -> saleService.registerSale(req, "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cobros QR");
        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    @DisplayName("T-17: la venta QR no exige la cuenta Caja")
    void shouldRegisterQrSaleWithoutCashAccount() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(saleRepository.nextSaleNumber()).thenReturn(3L);
        when(saleRepository.save(any(Sale.class))).thenAnswer(i -> {
            Sale s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        stubSaleFinance(List.of(qrAcc), Optional.of(salesCat));

        saleService.registerSale(singleItemSale(PaymentMethod.QR), "cajero");

        assertThat(qrAcc.getCurrentBalance()).isEqualByComparingTo("220.00");
        verify(financialTransactionRepository, times(1)).save(any(FinancialTransaction.class));
    }

    @Test
    @DisplayName("RG-03: rechaza la anulación de venta sin motivo")
    void shouldRejectSaleVoidWithoutReason() {
        UUID saleId = UUID.randomUUID();

        assertThatThrownBy(() -> saleService.voidSale(saleId, new VoidSaleRequest("   "), "supervisor"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("motivo de anulación es obligatorio");
        assertThatThrownBy(() -> saleService.voidSale(saleId, null, "supervisor"))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(saleRepository, cashSessionRepository);
    }
}
