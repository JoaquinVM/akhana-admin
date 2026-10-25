package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.PriceSuggestionResponse;
import com.akhana.akhana_admin.dto.PurchaseItemRequest;
import com.akhana.akhana_admin.dto.PurchasePaymentRequest;
import com.akhana.akhana_admin.dto.PurchaseRequest;
import com.akhana.akhana_admin.dto.PurchaseResponse;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.PurchaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseServiceTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductPriceChangeRepository productPriceChangeRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private FinancialAccountRepository financialAccountRepository;
    @Mock private FinancialCategoryRepository financialCategoryRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;

    private PurchaseServiceImpl purchaseService;

    private Supplier supplier;
    private Product nuts;
    private Product tea;
    private FinancialAccount bisa;
    private FinancialAccount caja;
    private FinancialCategory pedido;
    private FinancialCategory transporte;
    private CashSession openSession;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseServiceImpl(purchaseRepository, supplierRepository, productRepository,
            productPriceChangeRepository, cashSessionRepository, financialAccountRepository, financialCategoryRepository,
            financialTransactionRepository, new ExpectedCashCalculator(financialTransactionRepository));

        supplier = Supplier.builder().id(UUID.randomUUID()).name("Granel").code("GR").status(SupplierStatus.ACTIVO).build();
        nuts = Product.builder().id(UUID.randomUUID()).code("NUE-1").name("Nueces").saleUnit(SaleUnit.KILOGRAM)
            .buyPrice(new BigDecimal("40.00")).sellPrice(new BigDecimal("60.00"))
            .fixedProfit(new BigDecimal("20.00")).percentageProfit(new BigDecimal("50.00"))
            .status(ProductStatus.ACTIVO).build();
        tea = Product.builder().id(UUID.randomUUID()).code("TE-1").name("Té verde").saleUnit(SaleUnit.UNIT)
            .buyPrice(new BigDecimal("10.00")).sellPrice(new BigDecimal("15.00"))
            .fixedProfit(new BigDecimal("5.00")).percentageProfit(new BigDecimal("50.00"))
            .status(ProductStatus.ACTIVO).build();
        bisa = FinancialAccount.builder().id(UUID.randomUUID()).name("Bisa")
            .currentBalance(new BigDecimal("1000.00")).isCashAccount(false).build();
        caja = FinancialAccount.builder().id(UUID.randomUUID()).name("Caja")
            .currentBalance(new BigDecimal("500.00")).isCashAccount(true).build();
        pedido = FinancialCategory.builder().id(UUID.randomUUID()).name("Pedido").type(FinancialCategoryType.GASTO)
            .systemKey(FinancialCategorySystemKey.PURCHASE_PAYMENT).build();
        transporte = FinancialCategory.builder().id(UUID.randomUUID()).name("Transporte").type(FinancialCategoryType.GASTO)
            .systemKey(FinancialCategorySystemKey.PURCHASE_SHIPPING).build();
        openSession = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00")).totalSalesCash(BigDecimal.ZERO).build();

        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(productRepository.findById(nuts.getId())).thenReturn(Optional.of(nuts));
        when(productRepository.findById(tea.getId())).thenReturn(Optional.of(tea));
        when(purchaseRepository.nextPurchaseNumber()).thenReturn(7L);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> {
            Purchase p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(inv -> {
            FinancialTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            return tx;
        });
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_PAYMENT))
            .thenReturn(Optional.of(pedido));
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_SHIPPING))
            .thenReturn(Optional.of(transporte));
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(caja.getId())).thenReturn(true);
        when(financialAccountRepository.findActiveByIdForUpdate(bisa.getId())).thenReturn(Optional.of(bisa));
        when(financialAccountRepository.findActiveByIdForUpdate(caja.getId())).thenReturn(Optional.of(caja));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(any())).thenReturn(BigDecimal.ZERO);
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(any())).thenReturn(BigDecimal.ZERO);
    }

    private PurchaseItemRequest line(Product product, String quantity, String unitCost) {
        return new PurchaseItemRequest(product.getId(), new BigDecimal(quantity), new BigDecimal(unitCost), false, null);
    }

    private PurchaseRequest request(List<PurchaseItemRequest> items, String shipping, UUID shippingAccountId, boolean consignment) {
        return new PurchaseRequest(supplier.getId(), null, items, shipping != null ? new BigDecimal(shipping) : null,
            shippingAccountId, consignment, "  Pedido semanal  ");
    }

    private Purchase existingPurchase(String itemsTotal, String paid) {
        Purchase purchase = Purchase.builder()
            .id(UUID.randomUUID())
            .purchaseNumber("PED-000001")
            .supplier(supplier)
            .purchaseDate(LocalDate.now())
            .itemsTotal(new BigDecimal(itemsTotal))
            .shippingCost(new BigDecimal("0.00"))
            .totalAmount(new BigDecimal(itemsTotal))
            .paidAmount(new BigDecimal(paid))
            .payments(new ArrayList<>())
            .items(new ArrayList<>())
            .build();
        when(purchaseRepository.findByIdForUpdate(purchase.getId())).thenReturn(Optional.of(purchase));
        when(purchaseRepository.findById(purchase.getId())).thenReturn(Optional.of(purchase));
        return purchase;
    }

    // ---------------------------------------------------------------- COM-02

    @Test
    @DisplayName("COM-02: el pedido recibe número correlativo, líneas en escala 3 y totales con y sin envío")
    void registerPurchase_computesNumberLinesAndTotals() {
        PurchaseResponse res = purchaseService.registerPurchase(request(List.of(
            line(nuts, "1.5", "40"),
            line(tea, "24", "10.00")), "25.00", bisa.getId(), false), "operator");

        assertThat(res.purchaseNumber()).isEqualTo("PED-000007");
        assertThat(res.items()).hasSize(2);
        assertThat(res.items().get(0).quantity()).isEqualByComparingTo("1.500");
        assertThat(res.items().get(0).quantity().scale()).isEqualTo(3);
        assertThat(res.items().get(0).subtotal()).isEqualByComparingTo("60.00");
        assertThat(res.items().get(0).previousUnitCost()).isEqualByComparingTo("40.00");
        assertThat(res.itemsTotal()).isEqualByComparingTo("300.00");
        assertThat(res.shippingCost()).isEqualByComparingTo("25.00");
        assertThat(res.totalAmount()).isEqualByComparingTo("325.00");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.POR_PAGAR);
        assertThat(res.balance()).isEqualByComparingTo("300.00");
        assertThat(res.comment()).isEqualTo("Pedido semanal");
        assertThat(res.purchaseDate()).isEqualTo(LocalDate.now(ZoneId.of("America/La_Paz")));
        assertThat(res.createdBy()).isEqualTo("operator");
    }

    @Test
    @DisplayName("COM-02: un producto por unidad no acepta cantidades decimales")
    void registerPurchase_rejectsDecimalQuantityForUnitProduct() {
        assertThatThrownBy(() -> purchaseService.registerPurchase(
            request(List.of(line(tea, "1.5", "10")), null, null, false), "operator"))
            .hasMessage("La cantidad de 'Té verde' debe ser entera: se vende por unidad.");
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("COM-02: se rechazan pedidos sin líneas, con proveedor inactivo, fecha futura o producto eliminado")
    void registerPurchase_rejectsInvalidInput() {
        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(), null, null, false), "operator"))
            .hasMessage("El pedido debe tener al menos una línea.");

        PurchaseRequest future = new PurchaseRequest(supplier.getId(), LocalDate.now(ZoneId.of("America/La_Paz")).plusDays(1),
            List.of(line(tea, "1", "10")), null, null, false, null);
        assertThatThrownBy(() -> purchaseService.registerPurchase(future, "operator"))
            .hasMessage("La fecha del pedido no puede ser futura.");

        tea.setStatus(ProductStatus.ELIMINADO);
        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(line(tea, "1", "10")), null, null, false), "operator"))
            .hasMessage("El producto 'Té verde' está eliminado.");

        supplier.setStatus(SupplierStatus.INACTIVO);
        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(line(nuts, "1", "10")), null, null, false), "operator"))
            .hasMessage("El proveedor 'Granel' no está activo.");
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("COM-02: un costo unitario 0 se acepta (bonificación) y uno negativo no")
    void registerPurchase_acceptsZeroCostRejectsNegative() {
        PurchaseResponse res = purchaseService.registerPurchase(request(List.of(line(tea, "2", "0")), null, null, false), "operator");
        assertThat(res.itemsTotal()).isEqualByComparingTo("0.00");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);

        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(line(tea, "2", "-1")), null, null, false), "operator"))
            .hasMessage("El costo unitario de 'Té verde' no puede ser negativo.");
    }

    // ---------------------------------------------------------------- Envío (P-24)

    @Test
    @DisplayName("P-24: el envío se registra como gasto de Transporte vinculado al pedido en la cuenta elegida")
    void registerPurchase_recordsShippingExpense() {
        PurchaseResponse res = purchaseService.registerPurchase(
            request(List.of(line(tea, "10", "10")), "25", bisa.getId(), false), "operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        FinancialTransaction tx = captor.getValue();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.GASTO_MANUAL);
        assertThat(tx.getCategory()).isSameAs(transporte);
        assertThat(tx.getAmount()).isEqualByComparingTo("25.00");
        assertThat(tx.getPurchaseId()).isEqualTo(res.id());
        assertThat(tx.getCashSessionId()).isNull();
        assertThat(tx.getDescription()).isEqualTo("Envío pedido PED-000007");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("975.00");
        assertThat(res.shippingAccountName()).isEqualTo("Bisa");
        // Shipping is paid to the carrier: it does not count toward the supplier balance
        assertThat(res.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("P-24: sin envío no se crea movimiento; con envío se exige la cuenta")
    void registerPurchase_shippingRules() {
        purchaseService.registerPurchase(request(List.of(line(tea, "1", "10")), "0", null, false), "operator");
        verify(financialTransactionRepository, never()).save(any());

        assertThatThrownBy(() -> purchaseService.registerPurchase(
            request(List.of(line(tea, "1", "10")), "5", null, false), "operator"))
            .hasMessage("Indique la cuenta con la que se pagó el envío.");
    }

    @Test
    @DisplayName("P-24: sin categoría Transporte el pedido no se guarda")
    void registerPurchase_failsWithoutShippingCategory() {
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_SHIPPING))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.registerPurchase(
            request(List.of(line(tea, "1", "10")), "5", bisa.getId(), false), "operator"))
            .hasMessage("No existe la categoría Transporte; no se puede registrar el envío.");
        verify(purchaseRepository, never()).save(any());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-03: el envío pagado desde Caja exige caja abierta y baja el efectivo esperado")
    void registerPurchase_shippingFromCaja() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> purchaseService.registerPurchase(
            request(List.of(line(tea, "1", "10")), "5", caja.getId(), false), "operator"))
            .hasMessage("Los movimientos de la cuenta Caja requieren una caja abierta.");
        verify(purchaseRepository, never()).save(any());

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(openSession.getId())).thenReturn(new BigDecimal("5.00"));
        purchaseService.registerPurchase(request(List.of(line(tea, "1", "10")), "5", caja.getId(), false), "operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCashSessionId()).isEqualTo(openSession.getId());
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("95.00");
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("495.00");
        verify(cashSessionRepository).save(openSession);
    }

    // ---------------------------------------------------------------- COM-03

    @Test
    @DisplayName("COM-03: precio sugerido mantiene la utilidad porcentual, redondeado hacia arriba al boliviano")
    void suggestPrice_keepsPercentageProfitRoundedUp() {
        PriceSuggestionResponse s1 = purchaseService.suggestPrice(tea.getId(), new BigDecimal("12.30"));
        assertThat(s1.suggestedSellPrice()).isEqualByComparingTo("19.00");
        assertThat(s1.currentCost()).isEqualByComparingTo("10.00");
        assertThat(s1.currentSellPrice()).isEqualByComparingTo("15.00");

        assertThat(purchaseService.suggestPrice(tea.getId(), new BigDecimal("12")).suggestedSellPrice())
            .isEqualByComparingTo("18.00");

        tea.setBuyPrice(BigDecimal.ZERO);
        assertThat(purchaseService.suggestPrice(tea.getId(), new BigDecimal("12")).suggestedSellPrice()).isNull();
    }

    @Test
    @DisplayName("COM-03: sin confirmación el costo y precio del producto no cambian")
    void registerPurchase_keepsProductCostWithoutConfirmation() {
        PurchaseResponse res = purchaseService.registerPurchase(
            request(List.of(line(tea, "10", "12.30")), null, null, false), "operator");

        assertThat(tea.getBuyPrice()).isEqualByComparingTo("10.00");
        assertThat(tea.getSellPrice()).isEqualByComparingTo("15.00");
        assertThat(res.items().get(0).costUpdated()).isFalse();
        assertThat(res.items().get(0).previousUnitCost()).isEqualByComparingTo("10.00");
        verify(productPriceChangeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("COM-03: al confirmar, el producto toma el costo de la línea y el precio elegido, con auditoría")
    void registerPurchase_updatesProductCostAndPriceWithAudit() {
        PurchaseItemRequest item = new PurchaseItemRequest(tea.getId(), new BigDecimal("10"), new BigDecimal("12.30"),
            true, new BigDecimal("19"));
        PurchaseResponse res = purchaseService.registerPurchase(request(List.of(item), null, null, false), "operator");

        assertThat(tea.getBuyPrice()).isEqualByComparingTo("12.30");
        assertThat(tea.getSellPrice()).isEqualByComparingTo("19.00");
        assertThat(tea.getFixedProfit()).isEqualByComparingTo("6.70");
        assertThat(tea.getPercentageProfit()).isEqualByComparingTo("54.47");
        assertThat(tea.getUpdatedBy()).isEqualTo("operator");
        assertThat(res.items().get(0).costUpdated()).isTrue();

        ArgumentCaptor<ProductPriceChange> captor = ArgumentCaptor.forClass(ProductPriceChange.class);
        verify(productPriceChangeRepository).save(captor.capture());
        ProductPriceChange change = captor.getValue();
        assertThat(change.getPreviousBuyPrice()).isEqualByComparingTo("10.00");
        assertThat(change.getNewBuyPrice()).isEqualByComparingTo("12.30");
        assertThat(change.getPreviousSellPrice()).isEqualByComparingTo("15.00");
        assertThat(change.getNewSellPrice()).isEqualByComparingTo("19.00");
        assertThat(change.getSource()).isEqualTo(PriceChangeSource.PURCHASE);
        assertThat(change.getPurchaseId()).isEqualTo(res.id());
        assertThat(change.getChangedBy()).isEqualTo("operator");
    }

    @Test
    @DisplayName("COM-03: al confirmar sin precio nuevo se mantiene el precio actual")
    void registerPurchase_updatesCostKeepingPrice() {
        PurchaseItemRequest item = new PurchaseItemRequest(tea.getId(), new BigDecimal("1"), new BigDecimal("11"), true, null);
        purchaseService.registerPurchase(request(List.of(item), null, null, false), "operator");

        assertThat(tea.getBuyPrice()).isEqualByComparingTo("11.00");
        assertThat(tea.getSellPrice()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("COM-03: se rechazan dos líneas que actualizan el mismo producto, costo 0 o precio 0")
    void registerPurchase_rejectsInvalidCostUpdates() {
        PurchaseItemRequest a = new PurchaseItemRequest(tea.getId(), BigDecimal.ONE, new BigDecimal("11"), true, null);
        PurchaseItemRequest b = new PurchaseItemRequest(tea.getId(), BigDecimal.ONE, new BigDecimal("12"), true, null);
        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(a, b), null, null, false), "operator"))
            .hasMessage("El producto 'Té verde' tiene más de una línea que actualiza su costo.");

        PurchaseItemRequest zeroCost = new PurchaseItemRequest(tea.getId(), BigDecimal.ONE, BigDecimal.ZERO, true, null);
        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(zeroCost), null, null, false), "operator"))
            .hasMessage("El costo de 'Té verde' debe ser mayor a 0 para actualizar el producto.");

        PurchaseItemRequest zeroPrice = new PurchaseItemRequest(tea.getId(), BigDecimal.ONE, new BigDecimal("11"), true, BigDecimal.ZERO);
        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(zeroPrice), null, null, false), "operator"))
            .hasMessage("El precio de venta de 'Té verde' debe ser mayor a 0.");

        assertThat(tea.getBuyPrice()).isEqualByComparingTo("10.00");
        verify(purchaseRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- COM-04

    @Test
    @DisplayName("COM-04: el estado de pago sale de lo pagado frente al total de las líneas")
    void paymentStatus_isDerivedFromPayments() {
        Purchase p = Purchase.builder().itemsTotal(new BigDecimal("300.00")).paidAmount(new BigDecimal("0.00"))
            .consignment(false).build();
        assertThat(p.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.POR_PAGAR);
        p.setConsignment(true);
        assertThat(p.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.CONSIGNACION);
        p.setPaidAmount(new BigDecimal("100.00"));
        assertThat(p.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.A_CUENTA);
        assertThat(p.getBalance()).isEqualByComparingTo("200.00");
        p.setPaidAmount(new BigDecimal("300.00"));
        assertThat(p.getPaymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);
        assertThat(p.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("COM-04: el listado filtra por estado de pago")
    void getPurchases_filtersByPaymentStatus() {
        Purchase pending = Purchase.builder().id(UUID.randomUUID()).purchaseNumber("PED-000001").supplier(supplier)
            .itemsTotal(new BigDecimal("100.00")).paidAmount(new BigDecimal("0.00")).consignment(false)
            .shippingCost(BigDecimal.ZERO).totalAmount(new BigDecimal("100.00")).build();
        Purchase paid = Purchase.builder().id(UUID.randomUUID()).purchaseNumber("PED-000002").supplier(supplier)
            .itemsTotal(new BigDecimal("100.00")).paidAmount(new BigDecimal("100.00")).consignment(false)
            .shippingCost(BigDecimal.ZERO).totalAmount(new BigDecimal("100.00")).build();
        when(purchaseRepository.search(null, "PED")).thenReturn(List.of(pending, paid));

        List<PurchaseResponse> res = purchaseService.getPurchases(null, PurchasePaymentStatus.POR_PAGAR, "  PED ");
        assertThat(res).extracting(PurchaseResponse::purchaseNumber).containsExactly("PED-000001");
        assertThat(purchaseService.getPurchases(null, null, "PED")).hasSize(2);
    }

    // ---------------------------------------------------------------- COM-05 / COM-06

    @Test
    @DisplayName("COM-05: un pago desde Bisa crea un gasto Pedido vinculado y deja el pedido a cuenta")
    void registerPayment_fromAccount() {
        Purchase purchase = existingPurchase("300.00", "0.00");

        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), new BigDecimal("100"), null), "operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        FinancialTransaction tx = captor.getValue();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.GASTO_MANUAL);
        assertThat(tx.getCategory()).isSameAs(pedido);
        assertThat(tx.getPurchaseId()).isEqualTo(purchase.getId());
        assertThat(tx.getCashSessionId()).isNull();
        assertThat(tx.getDescription()).isEqualTo("Pago pedido PED-000001");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("900.00");

        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.A_CUENTA);
        assertThat(res.paidAmount()).isEqualByComparingTo("100.00");
        assertThat(res.balance()).isEqualByComparingTo("200.00");
        assertThat(res.payments()).hasSize(1);
        assertThat(res.payments().get(0).accountName()).isEqualTo("Bisa");
        assertThat(res.payments().get(0).financialTransactionId()).isEqualTo(tx.getId());
    }

    @Test
    @DisplayName("COM-05: pagar el saldo deja el pedido pagado; un pago mayor al saldo o en 0 se rechaza")
    void registerPayment_balanceRules() {
        Purchase purchase = existingPurchase("300.00", "100.00");

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), new BigDecimal("200.01"), null), "operator"))
            .hasMessage("El pago supera el saldo pendiente del pedido (Bs 200.00).");
        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), new BigDecimal("0.004"), null), "operator"))
            .hasMessage("El monto debe ser estrictamente mayor a 0.00 Bs.");
        verify(financialTransactionRepository, never()).save(any());

        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), new BigDecimal("200"), null), "operator");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);
        assertThat(res.balance()).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), new BigDecimal("1"), null), "operator"))
            .hasMessage("El pago supera el saldo pendiente del pedido (Bs 0.00).");
    }

    @Test
    @DisplayName("COM-05: sin categoría Pedido el pago no se guarda")
    void registerPayment_failsWithoutPaymentCategory() {
        Purchase purchase = existingPurchase("300.00", "0.00");
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_PAYMENT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), new BigDecimal("50"), null), "operator"))
            .hasMessage("No existe la categoría Pedido; no se puede registrar el pago.");
        assertThat(purchase.getPaidAmount()).isEqualByComparingTo("0.00");
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("COM-06: un pago desde Caja exige caja abierta, se vincula a ella y baja el efectivo esperado")
    void registerPayment_fromCaja() {
        Purchase purchase = existingPurchase("300.00", "0.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(caja.getId(), new BigDecimal("50"), null), "operator"))
            .hasMessage("Los movimientos de la cuenta Caja requieren una caja abierta.");
        assertThat(purchase.getPaidAmount()).isEqualByComparingTo("0.00");

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(openSession.getId())).thenReturn(new BigDecimal("50.00"));
        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(caja.getId(), new BigDecimal("50"), "Pagó Juan"), "operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCashSessionId()).isEqualTo(openSession.getId());
        assertThat(captor.getValue().getNotes()).isEqualTo("Pagó Juan");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("50.00");
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("450.00");
        assertThat(res.payments().get(0).cashAccount()).isTrue();
    }

    @Test
    @DisplayName("COM-05: el pedido se bloquea antes que la caja y la cuenta (T-14)")
    void registerPayment_locksPurchaseFirst() {
        Purchase purchase = existingPurchase("300.00", "0.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));

        purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(caja.getId(), new BigDecimal("50"), null), "operator");

        var order = inOrder(purchaseRepository, cashSessionRepository, financialAccountRepository);
        order.verify(purchaseRepository).findByIdForUpdate(purchase.getId());
        order.verify(cashSessionRepository).findByStatusForUpdate(CashSessionStatus.ABIERTA);
        order.verify(financialAccountRepository).findActiveByIdForUpdate(caja.getId());
    }
}
