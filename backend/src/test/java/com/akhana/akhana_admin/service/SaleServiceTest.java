package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleItemResponse;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.CreditorRepository;
import com.akhana.akhana_admin.repository.SaleItemRepository;
import com.akhana.akhana_admin.repository.CustomerRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.repository.ProductPresentationRepository;
import com.akhana.akhana_admin.repository.ProductPriceChangeRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SaleRepository;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.ProductPresentationServiceImpl;
import com.akhana.akhana_admin.service.impl.SaleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductPresentationRepository presentationRepository;

    @Mock
    private ProductPriceChangeRepository priceChangeRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private CreditorRepository creditorRepository;

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
            financialCategoryRepository, financialTransactionRepository, new ExpectedCashCalculator(financialTransactionRepository),
            customerRepository, new ProductPresentationServiceImpl(presentationRepository, priceChangeRepository),
            saleItemRepository, creditorRepository);
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
            .buyPrice(new BigDecimal("12.50"))
            .sellPrice(new BigDecimal("20.00"))
            .status(ProductStatus.ACTIVO)
            .build();

        product2 = Product.builder()
            .id(UUID.randomUUID())
            .code("PRD-002")
            .name("Té Verde")
            .buyPrice(new BigDecimal("9.00"))
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
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal("1"), BigDecimal.ZERO)),
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

        SaleItemRequest itemReq = new SaleItemRequest(product1.getId(), new BigDecimal("2"), new BigDecimal("2.00")); // final 18 * 2 = 36
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

        SaleItemRequest item1 = new SaleItemRequest(product1.getId(), new BigDecimal("2"), BigDecimal.ZERO); // 40
        SaleItemRequest item2 = new SaleItemRequest(product2.getId(), new BigDecimal("4"), BigDecimal.ZERO); // 60 -> total 100
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
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal("1"), BigDecimal.ZERO)),
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
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal("2"), BigDecimal.ZERO)),
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
    @DisplayName("POS-04: un pago mixto con todo en efectivo o todo en QR se rechaza")
    void shouldRejectMixedPaymentWithOnePart() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        for (String cash : List.of("0.00", "40.00")) {
            SaleDetailRequest req = new SaleDetailRequest(
                List.of(new SaleItemRequest(product1.getId(), new BigDecimal("2"), BigDecimal.ZERO)),
                BigDecimal.ZERO, PaymentMethod.MIXTO, new BigDecimal(cash), null, new BigDecimal(cash), null);
            assertThatThrownBy(() -> saleService.registerSale(req, "cajero"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("En un pago mixto el efectivo y el QR deben ser mayores a Bs 0,00; si se paga todo de una forma, elija Efectivo o QR.");
        }
        verify(saleRepository, never()).save(any(Sale.class));
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
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal("1"), BigDecimal.ZERO)),
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

    @Test
    @DisplayName("Debe guardar el costo del producto en cada línea y no cambiarlo si luego cambia el producto o se anula la venta (POS-05)")
    void shouldSnapshotProductCostOnSaleItems() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
        when(saleRepository.nextSaleNumber()).thenReturn(1L);
        List<Sale> saved = new ArrayList<>();
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            saved.add(s);
            return s;
        });

        SaleDetailRequest req = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal("2"), BigDecimal.ZERO),
                    new SaleItemRequest(product2.getId(), new BigDecimal("1"), BigDecimal.ZERO)),
            BigDecimal.ZERO, PaymentMethod.EFECTIVO, new BigDecimal("55.00"), BigDecimal.ZERO, new BigDecimal("55.00"), null);
        saleService.registerSale(req, "cajero");

        Sale sale = saved.get(0);
        assertThat(sale.getItems()).extracting(SaleItem::getUnitCost)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("12.50"), new BigDecimal("9.00"));
        assertThat(sale.getItems().get(0).getUnitCost().scale()).isEqualTo(2);

        product1.setBuyPrice(new BigDecimal("14.00"));
        product2.setBuyPrice(new BigDecimal("7.25"));

        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        saleService.voidSale(sale.getId(), new VoidSaleRequest("Cliente devolvió los productos"), "supervisor");

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.ANULADA);
        assertThat(sale.getItems()).extracting(SaleItem::getUnitCost)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("12.50"), new BigDecimal("9.00"));
    }

    @Test
    @DisplayName("CAT-08: registra cantidades decimales por peso con subtotales en escala 2 HALF_UP")
    void shouldRegisterDecimalQuantityWithRoundedSubtotals() {
        product1.setSaleUnit(SaleUnit.KILOGRAM);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(saleRepository.nextSaleNumber()).thenReturn(8L);
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
        when(saleRepository.save(saleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // 20.00 x 0.375 = 7.50 gross; (20.00 - 0.05) x 0.375 = 7.48125 -> 7.48; discount 7.50 - 7.48 = 0.02
        SaleDetailRequest req = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal("0.375"), new BigDecimal("0.05"))),
            BigDecimal.ZERO,
            PaymentMethod.EFECTIVO,
            null,
            null,
            new BigDecimal("10.00"),
            null
        );

        SaleResponse response = saleService.registerSale(req, "cajero");

        assertThat(response.subtotalAmount()).isEqualByComparingTo("7.50");
        assertThat(response.discountItemsTotal()).isEqualByComparingTo("0.02");
        assertThat(response.totalAmount()).isEqualByComparingTo("7.48");
        assertThat(response.changeGiven()).isEqualByComparingTo("2.52");
        assertThat(response.items().get(0).quantity()).isEqualByComparingTo("0.375");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("7.48");

        SaleItem item = saleCaptor.getValue().getItems().get(0);
        assertThat(item.getQuantity().scale()).isEqualTo(3);
        assertThat(item.getSubtotal().scale()).isEqualTo(2);
        assertThat(activeSession.getExpectedCash()).isEqualByComparingTo("107.48");
    }

    @Test
    @DisplayName("CAT-08: rechaza cantidades en cero, negativas o con más de 3 decimales")
    void shouldRejectInvalidDecimalQuantities() {
        product1.setSaleUnit(SaleUnit.KILOGRAM);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        for (String qty : List.of("0", "0.000", "-1")) {
            assertThatThrownBy(() -> saleService.registerSale(saleWithQuantity(qty), "cajero"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debe ser mayor a 0");
        }
        assertThatThrownBy(() -> saleService.registerSale(saleWithQuantity("0.1255"), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("hasta 3 decimales");

        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    @DisplayName("POS-10: un producto por unidad rechaza cantidades con decimales")
    void shouldRejectDecimalQuantityForUnitProduct() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        assertThatThrownBy(() -> saleService.registerSale(saleWithQuantity("0.5"), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("debe ser entera");

        verify(saleRepository, never()).save(any(Sale.class));
    }

    // ---------------------------------------------------------------------------------------------
    // CAT-11 presentations: Nuez pelada at 130 per kilo cost and 210 per kilo price
    // ---------------------------------------------------------------------------------------------

    private ProductPresentation nutPresentation(String name, String weight, String fixedPrice, boolean active) {
        product1.setName("Nuez pelada");
        product1.setSaleUnit(SaleUnit.KILOGRAM);
        product1.setBuyPrice(new BigDecimal("130.00"));
        product1.setSellPrice(new BigDecimal("210.00"));
        ProductPresentation presentation = ProductPresentation.builder().id(UUID.randomUUID()).product(product1)
            .name(name).weightKg(new BigDecimal(weight)).fixedPrice(fixedPrice != null ? new BigDecimal(fixedPrice) : null)
            .active(active).build();
        lenient().when(presentationRepository.findById(presentation.getId())).thenReturn(Optional.of(presentation));
        return presentation;
    }

    private SaleDetailRequest presentationSale(ProductPresentation presentation, String packs, SaleType type) {
        return new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal(packs), BigDecimal.ZERO, presentation.getId())),
            BigDecimal.ZERO, PaymentMethod.EFECTIVO, null, null, null, null, type, null, null);
    }

    private Sale registerCapturing(SaleDetailRequest request) {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(saleRepository.nextSaleNumber()).thenReturn(9L);
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
        when(saleRepository.save(saleCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        saleService.registerSale(request, "cajero");
        return saleCaptor.getValue();
    }

    @Test
    @DisplayName("CAT-11: 2 x 1/2 libra con precio fijo cobra 90, guarda 0,454 kg y el costo por kilo")
    void shouldSellByPresentationWithFixedPrice() {
        ProductPresentation halfPound = nutPresentation("1/2 libra", "0.227", "45.00", true);

        Sale sale = registerCapturing(presentationSale(halfPound, "2", SaleType.NORMAL));

        SaleItem item = sale.getItems().get(0);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("45.00");
        assertThat(item.getQuantity()).isEqualByComparingTo("2");
        assertThat(item.getSubtotal()).isEqualByComparingTo("90.00");
        assertThat(item.getUnitCost()).isEqualByComparingTo("130.00");
        assertThat(item.getWeightKg()).isEqualByComparingTo("0.454");
        assertThat(item.getPresentation()).isSameAs(halfPound);
        assertThat(item.getPresentationName()).isEqualTo("1/2 libra");
        assertThat(item.getPresentationWeightKg()).isEqualByComparingTo("0.227");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("CAT-11: la línea de «Otro peso» informa que se vende por kilo para mostrar kg y /kg")
    void shouldReportSaleUnitOfWeightLineWithoutPresentation() {
        nutPresentation("1/2 libra", "0.227", "45.00", true);
        SaleItem otherWeight = SaleItem.builder().product(product1).productName("Nuez pelada")
            .unitPrice(new BigDecimal("210.00")).quantity(new BigDecimal("0.300")).subtotal(new BigDecimal("63.00")).build();

        SaleItemResponse response = SaleItemResponse.fromEntity(otherWeight);

        assertThat(response.saleUnit()).isEqualTo(SaleUnit.KILOGRAM);
        assertThat(response.weightKg()).isNull();
    }

    @Test
    @DisplayName("CAT-11: sin precio fijo, 100 g cuesta 0,100 x 210 = 21,00")
    void shouldSellByPresentationWithComputedPrice() {
        ProductPresentation hundredGrams = nutPresentation("100 g", "0.100", null, true);

        Sale sale = registerCapturing(presentationSale(hundredGrams, "1", SaleType.NORMAL));

        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("21.00");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("21.00");
    }

    @Test
    @DisplayName("CAT-11: al costo, la 1/2 libra cobra 0,227 x 130 = 29,51 e ignora el precio fijo")
    void shouldSellPresentationAtCostIgnoringFixedPrice() {
        ProductPresentation halfPound = nutPresentation("1/2 libra", "0.227", "45.00", true);

        Sale sale = registerCapturing(presentationSale(halfPound, "1", SaleType.AL_COSTO));

        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("29.51");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("29.51");
    }

    @Test
    @DisplayName("CAT-11: rechaza una presentación inactiva y una cantidad con decimales")
    void shouldRejectInactivePresentationAndDecimalPacks() {
        ProductPresentation inactive = nutPresentation("1 libra", "0.454", "90.00", false);
        ProductPresentation halfPound = nutPresentation("1/2 libra", "0.227", "45.00", true);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        assertThatThrownBy(() -> saleService.registerSale(presentationSale(inactive, "1", SaleType.NORMAL), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La presentación '1 libra' de 'Nuez pelada' ya no está disponible.");
        assertThatThrownBy(() -> saleService.registerSale(presentationSale(halfPound, "1.5", SaleType.NORMAL), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La cantidad de 'Nuez pelada · 1/2 libra' debe ser entera: se vende por presentación.");
        verify(saleRepository, never()).save(any(Sale.class));
    }

    private SaleDetailRequest saleWithQuantity(String quantity) {
        return new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), new BigDecimal(quantity), BigDecimal.ZERO)),
            BigDecimal.ZERO, PaymentMethod.EFECTIVO, null, null, null, null);
    }

    // ---------------------------------------------------------------------------------------------
    // CON-02 at-cost sales and DEU-05 credit
    // ---------------------------------------------------------------------------------------------

    private Customer tiaNeida() {
        return Customer.builder().id(UUID.randomUUID()).name("Tía Neida").status(CustomerStatus.ACTIVO).build();
    }

    private SaleDetailRequest atCostSale(PaymentMethod method, BigDecimal amountCash, BigDecimal amountReceived,
                                         UUID customerId, BigDecimal creditAmount, BigDecimal quantity) {
        return new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), quantity, BigDecimal.ZERO)),
            BigDecimal.ZERO, method, amountCash, null, amountReceived, null, SaleType.AL_COSTO, customerId, creditAmount);
    }

    private List<Sale> captureSavedSales() {
        when(saleRepository.nextSaleNumber()).thenReturn(1L);
        List<Sale> saved = new ArrayList<>();
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            saved.add(s);
            return s;
        });
        return saved;
    }

    @Test
    @DisplayName("CON-02: la venta al costo cobra el costo del producto, sin utilidad, e ingresa a Caja como Venta")
    void atCostSaleChargesTheProductCost() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        List<Sale> saved = captureSavedSales();

        SaleResponse response = saleService.registerSale(
            atCostSale(PaymentMethod.EFECTIVO, null, new BigDecimal("30.00"), null, null, new BigDecimal("2")), "cajero");

        Sale sale = saved.get(0);
        assertThat(sale.getSaleType()).isEqualTo(SaleType.AL_COSTO);
        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(sale.getItems().get(0).getUnitCost()).isEqualByComparingTo("12.50");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(sale.getChangeGiven()).isEqualByComparingTo("5.00");
        assertThat(sale.getCreditAmount()).isEqualByComparingTo("0.00");
        assertThat(sale.getCustomer()).isNull();
        assertThat(response.saleType()).isEqualTo(SaleType.AL_COSTO);
        assertThat(cashAcc.getCurrentBalance()).isEqualByComparingTo("125.00");
        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("P-39: la venta al costo pagada en el momento, sin cliente, guarda su comentario")
    void atCostSaleKeepsItsComment() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        List<Sale> saved = captureSavedSales();

        SaleResponse response = saleService.registerSale(new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), BigDecimal.ONE, BigDecimal.ZERO)), BigDecimal.ZERO,
            PaymentMethod.EFECTIVO, null, null, new BigDecimal("12.50"), "  Mami precio costo ", SaleType.AL_COSTO,
            null, null), "cajero");

        assertThat(saved.get(0).getDescription()).isEqualTo("Mami precio costo");
        assertThat(saved.get(0).getCustomer()).isNull();
        assertThat(response.description()).isEqualTo("Mami precio costo");
    }

    @Test
    @DisplayName("CON-02: la venta al costo rechaza descuentos por unidad y generales")
    void atCostSaleRejectsDiscounts() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        SaleDetailRequest unitDiscount = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), BigDecimal.ONE, new BigDecimal("1.00"))),
            BigDecimal.ZERO, PaymentMethod.EFECTIVO, null, null, null, null, SaleType.AL_COSTO, null, null);
        SaleDetailRequest globalDiscount = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), BigDecimal.ONE, BigDecimal.ZERO)),
            new BigDecimal("2.00"), PaymentMethod.EFECTIVO, null, null, null, null, SaleType.AL_COSTO, null, null);

        for (SaleDetailRequest req : List.of(unitDiscount, globalDiscount)) {
            assertThatThrownBy(() -> saleService.registerSale(req, "cajero"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Una venta al costo no admite descuentos.");
        }
        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    @DisplayName("CON-02: un producto con costo 0 no se vende al costo")
    void atCostSaleRejectsZeroCost() {
        product1.setBuyPrice(BigDecimal.ZERO);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        assertThatThrownBy(() -> saleService.registerSale(
                atCostSale(PaymentMethod.EFECTIVO, null, null, null, null, BigDecimal.ONE), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'Café Premium' no tiene costo registrado; no se puede vender al costo.");
        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    @DisplayName("CON-02: sin caja abierta la venta al costo se rechaza como toda venta")
    void atCostSaleNeedsOpenSession() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saleService.registerSale(
                atCostSale(PaymentMethod.EFECTIVO, null, null, null, null, BigDecimal.ONE), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("caja abierta");
    }

    @Test
    @DisplayName("DEU-05: toda a crédito queda con método CREDITO, sin movimiento financiero ni efectivo esperado")
    void fullCreditSaleHasNoMovement() {
        Customer neida = tiaNeida();
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(customerRepository.findByIdForUpdate(neida.getId())).thenReturn(Optional.of(neida));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(activeSession.getId())).thenReturn(BigDecimal.ZERO);
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(activeSession.getId())).thenReturn(BigDecimal.ZERO);
        List<Sale> saved = captureSavedSales();

        saleService.registerSale(atCostSale(PaymentMethod.EFECTIVO, null, null, neida.getId(),
            new BigDecimal("37.50"), new BigDecimal("3")), "cajero");

        Sale sale = saved.get(0);
        assertThat(sale.getPaymentMethod()).isEqualTo(PaymentMethod.CREDITO);
        assertThat(sale.getCreditAmount()).isEqualByComparingTo("37.50");
        assertThat(sale.getCustomer()).isSameAs(neida);
        assertThat(sale.getAmountCash()).isEqualByComparingTo("0.00");
        assertThat(sale.getAmountQr()).isEqualByComparingTo("0.00");
        verify(financialTransactionRepository, never()).save(any(FinancialTransaction.class));
        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("0.00");
        assertThat(activeSession.getExpectedCash()).isEqualByComparingTo("100.00");
        assertThat(cashAcc.getCurrentBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("DEU-05: parte por QR y parte a crédito; solo lo cobrado entra a la cuenta QR")
    void partialCreditSalePaysTheRestByQr() {
        product1.setBuyPrice(new BigDecimal("120.00"));
        Customer neida = tiaNeida();
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(customerRepository.findByIdForUpdate(neida.getId())).thenReturn(Optional.of(neida));
        List<Sale> saved = captureSavedSales();

        saleService.registerSale(atCostSale(PaymentMethod.QR, null, null, neida.getId(),
            new BigDecimal("80.00"), BigDecimal.ONE), "cajero");

        Sale sale = saved.get(0);
        assertThat(sale.getPaymentMethod()).isEqualTo(PaymentMethod.QR);
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("120.00");
        assertThat(sale.getAmountQr()).isEqualByComparingTo("40.00");
        assertThat(sale.getCreditAmount()).isEqualByComparingTo("80.00");
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("40.00");
        assertThat(txCaptor.getValue().getSourceAccount()).isSameAs(qrAcc);
        assertThat(qrAcc.getCurrentBalance()).isEqualByComparingTo("240.00");
    }

    @Test
    @DisplayName("DEU-05: parte en efectivo con cambio y parte a crédito")
    void partialCreditSalePaysTheRestInCash() {
        Customer neida = tiaNeida();
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        stubSaleFinance(List.of(cashAcc, qrAcc), Optional.of(salesCat));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(customerRepository.findByIdForUpdate(neida.getId())).thenReturn(Optional.of(neida));
        List<Sale> saved = captureSavedSales();

        // Total 50.00: 20.00 now (paid with 50.00), 30.00 on credit
        saleService.registerSale(atCostSale(PaymentMethod.EFECTIVO, null, new BigDecimal("50.00"), neida.getId(),
            new BigDecimal("30.00"), new BigDecimal("4")), "cajero");

        Sale sale = saved.get(0);
        assertThat(sale.getAmountCash()).isEqualByComparingTo("20.00");
        assertThat(sale.getChangeGiven()).isEqualByComparingTo("30.00");
        assertThat(activeSession.getTotalSalesCash()).isEqualByComparingTo("20.00");
        assertThat(activeSession.getTotalSales()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("DEU-05: una venta normal no puede quedar a crédito")
    void normalSaleRejectsCredit() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        SaleDetailRequest req = new SaleDetailRequest(
            List.of(new SaleItemRequest(product1.getId(), BigDecimal.ONE, BigDecimal.ZERO)),
            BigDecimal.ZERO, PaymentMethod.EFECTIVO, null, null, null, null, SaleType.NORMAL, UUID.randomUUID(),
            new BigDecimal("5.00"));

        assertThatThrownBy(() -> saleService.registerSale(req, "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Solo una venta al costo puede quedar a crédito.");
        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    @DisplayName("DEU-05: el crédito exige un cliente activo")
    void creditNeedsAnActiveCustomer() {
        Customer inactive = tiaNeida();
        inactive.setStatus(CustomerStatus.INACTIVO);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(customerRepository.findByIdForUpdate(inactive.getId())).thenReturn(Optional.of(inactive));

        for (UUID customerId : Arrays.asList(null, inactive.getId())) {
            assertThatThrownBy(() -> saleService.registerSale(atCostSale(PaymentMethod.EFECTIVO, null, null, customerId,
                    new BigDecimal("5.00"), BigDecimal.ONE), "cajero"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Elija un cliente activo para la venta a crédito.");
        }
        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    @DisplayName("DEU-05: el crédito no puede ser negativo ni superar el total; método Crédito exige todo a crédito")
    void creditMustBeWithinTheTotal() {
        Customer neida = tiaNeida();
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        for (String credit : List.of("-1.00", "12.51")) {
            assertThatThrownBy(() -> saleService.registerSale(atCostSale(PaymentMethod.EFECTIVO, null, null, neida.getId(),
                    new BigDecimal(credit), BigDecimal.ONE), "cajero"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El crédito debe estar entre Bs 0,00 y el total de la venta.");
        }
        assertThatThrownBy(() -> saleService.registerSale(atCostSale(PaymentMethod.CREDITO, null, null, neida.getId(),
                new BigDecimal("5.00"), BigDecimal.ONE), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lo cobrado más el crédito debe ser igual al total");
        verify(saleRepository, never()).save(any(Sale.class));
    }

    private Sale completedCreditSale(Customer customer, String credit) {
        return Sale.builder()
            .id(UUID.randomUUID())
            .saleNumber("VTA-000010")
            .cashSession(activeSession)
            .status(SaleStatus.COMPLETADA)
            .saleType(SaleType.AL_COSTO)
            .customer(customer)
            .creditAmount(new BigDecimal(credit))
            .totalAmount(new BigDecimal(credit))
            .paymentMethod(PaymentMethod.CREDITO)
            .build();
    }

    @Test
    @DisplayName("DEU-05: no se anula una venta a crédito ya cobrada; primero se anula el cobro")
    void voidCreditSaleAlreadyCollectedIsRejected() {
        Customer neida = tiaNeida();
        Sale sale = completedCreditSale(neida, "80.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(customerRepository.findByIdForUpdate(neida.getId())).thenReturn(Optional.of(neida));
        // 80.00 on credit, 50.00 already collected
        when(customerRepository.computeBalance(neida.getId())).thenReturn(new BigDecimal("30.00"));

        assertThatThrownBy(() -> saleService.voidSale(sale.getId(), new VoidSaleRequest("Error"), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Ya se cobró parte de esta venta a Tía Neida; anule primero el cobro.");
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETADA);
    }

    @Test
    @DisplayName("DEU-05: una venta a crédito sin cobros se anula con su caja abierta")
    void voidCreditSaleWithoutCollections() {
        Customer neida = tiaNeida();
        Sale sale = completedCreditSale(neida, "80.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(activeSession));
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.findByIdForUpdate(neida.getId())).thenReturn(Optional.of(neida));
        when(customerRepository.computeBalance(neida.getId())).thenReturn(new BigDecimal("80.00"));
        when(financialTransactionRepository.findBySaleId(sale.getId())).thenReturn(List.of());

        saleService.voidSale(sale.getId(), new VoidSaleRequest("Error de carga"), "cajero");

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.ANULADA);
    }

    @Test
    @DisplayName("POS-09: el historial filtra por días de la tienda y limita el tamaño de página")
    void searchSalesUsesStoreDaysAndCapsPageSize() {
        when(saleRepository.search(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(Page.empty());

        saleService.searchSales(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 25),
            SaleStatus.COMPLETADA, SaleType.AL_COSTO, PaymentMethod.CREDITO, " operador ", false, -1, 500);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        // Bolivia is UTC-4: the 1st starts at 04:00 UTC and the 25th ends at 04:00 UTC of the 26th
        verify(saleRepository).search(eq(Instant.parse("2026-10-01T04:00:00Z")), eq(Instant.parse("2026-10-26T04:00:00Z")),
            eq(SaleStatus.COMPLETADA), eq(SaleType.AL_COSTO), eq(PaymentMethod.CREDITO), eq("operador"), eq(false), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("POS-09: rechaza un rango de fechas invertido")
    void searchSalesRejectsInvertedRange() {
        assertThatThrownBy(() -> saleService.searchSales(LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 1), null, null, null, null, false, 0, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La fecha desde no puede ser posterior a la fecha hasta.");
        verifyNoInteractions(saleRepository);
    }
}

