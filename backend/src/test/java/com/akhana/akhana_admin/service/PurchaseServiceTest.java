package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.PayablesResponse;
import com.akhana.akhana_admin.dto.PriceSuggestionResponse;
import com.akhana.akhana_admin.dto.PurchaseDeliveryRequest;
import com.akhana.akhana_admin.dto.PurchaseItemRequest;
import com.akhana.akhana_admin.dto.PurchasePaymentRequest;
import com.akhana.akhana_admin.dto.PurchasePaymentResponse;
import com.akhana.akhana_admin.dto.PurchaseRequest;
import com.akhana.akhana_admin.dto.PurchaseResponse;
import com.akhana.akhana_admin.dto.PurchaseUpdateRequest;
import com.akhana.akhana_admin.dto.VoidPurchaseRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.PurchaseServiceImpl;
import com.akhana.akhana_admin.service.impl.StoreFormat;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    @Mock private CreditorRepository creditorRepository;

    private PurchaseServiceImpl purchaseService;

    private Supplier supplier;
    private Product nuts;
    private Product tea;
    private FinancialAccount bisa;
    private FinancialAccount caja;
    private FinancialCategory pedido;
    private FinancialCategory transporte;
    private CashSession openSession;
    private Creditor mami;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseServiceImpl(purchaseRepository, supplierRepository, productRepository,
            productPriceChangeRepository, cashSessionRepository, financialAccountRepository, financialCategoryRepository,
            financialTransactionRepository, new ExpectedCashCalculator(financialTransactionRepository), creditorRepository);

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
        mami = Creditor.builder().id(UUID.randomUUID()).name("Mami").status(CreditorStatus.ACTIVO).build();
        when(creditorRepository.findByIdForUpdate(mami.getId())).thenReturn(Optional.of(mami));
        when(creditorRepository.findAllByIdForUpdate(any())).thenReturn(List.of(mami));
        // CAJ-11: a session of 31-10 still open after midnight
        openSession = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00")).totalSalesCash(BigDecimal.ZERO)
            .operationDate(LocalDate.of(2026, 10, 31)).build();

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
            shippingAccountId, consignment, "  Pedido semanal  ", null, null, null, null);
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
            List.of(line(tea, "1", "10")), null, null, false, null, null, null, null, null);
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

    @Test
    @DisplayName("COM-02: un costo unitario con más de 2 decimales se rechaza en vez de redondearse")
    void registerPurchase_rejectsCostWithMoreThanTwoDecimals() {
        assertThatThrownBy(() -> purchaseService.registerPurchase(request(List.of(line(tea, "1", "30.555")), null, null, false), "operator"))
            .hasMessage("El costo unitario de 'Té verde' admite hasta 2 decimales.");
        verify(purchaseRepository, never()).save(any());

        PurchaseResponse res = purchaseService.registerPurchase(request(List.of(line(tea, "1", "30.500")), null, null, false), "operator");
        assertThat(res.itemsTotal()).isEqualByComparingTo("30.50");
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

    @Test
    @DisplayName("T-14: envío desde Bisa y primer pago desde Caja bloquean la caja antes que las cuentas, y las cuentas juntas")
    void registerPurchase_shippingAndPaymentLockSessionFirst() {
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        PurchaseRequest req = new PurchaseRequest(supplier.getId(), null, List.of(line(tea, "10", "10")), new BigDecimal("5"),
            bisa.getId(), false, null, null, null, null, new PurchasePaymentRequest(caja.getId(), null, new BigDecimal("50"), null));

        purchaseService.registerPurchase(req, "operator");

        var order = inOrder(cashSessionRepository, financialAccountRepository);
        order.verify(cashSessionRepository).findByStatusForUpdate(CashSessionStatus.ABIERTA);
        order.verify(financialAccountRepository).findAllByIdForUpdate(Set.of(bisa.getId(), caja.getId()));
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

        List<PurchaseResponse> res = purchaseService.getPurchases(null, null, PurchasePaymentStatus.POR_PAGAR, null, "  PED ");
        assertThat(res).extracting(PurchaseResponse::purchaseNumber).containsExactly("PED-000001");
        assertThat(purchaseService.getPurchases(null, null, null, null, "PED")).hasSize(2);
    }

    // ---------------------------------------------------------------- COM-05 / COM-06

    @Test
    @DisplayName("COM-05: un pago desde Bisa crea un gasto Pedido vinculado y deja el pedido a cuenta")
    void registerPayment_fromAccount() {
        Purchase purchase = existingPurchase("300.00", "0.00");

        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("100"), null), "operator");

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
        // CAJ-11: not from Caja, so the expense and the payment count for the day they are recorded
        assertThat(tx.getOperationDate()).isEqualTo(StoreFormat.storeDay(tx.getTransactionDate()));
        assertThat(res.payments().get(0).operationDate()).isEqualTo(tx.getOperationDate());
    }

    @Test
    @DisplayName("COM-05: pagar el saldo deja el pedido pagado; un pago mayor al saldo o en 0 se rechaza")
    void registerPayment_balanceRules() {
        Purchase purchase = existingPurchase("300.00", "100.00");

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("200.01"), null), "operator"))
            .hasMessage("El pago supera el saldo pendiente del pedido (Bs 200,00).");
        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("0.004"), null), "operator"))
            .hasMessage("El monto debe ser mayor a Bs 0,00.");
        verify(financialTransactionRepository, never()).save(any());

        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("200"), null), "operator");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);
        assertThat(res.balance()).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("1"), null), "operator"))
            .hasMessage("El pago supera el saldo pendiente del pedido (Bs 0,00).");
    }

    @Test
    @DisplayName("COM-05: sin categoría Pedido el pago no se guarda")
    void registerPayment_failsWithoutPaymentCategory() {
        Purchase purchase = existingPurchase("300.00", "0.00");
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.PURCHASE_PAYMENT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("50"), null), "operator"))
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
            new PurchasePaymentRequest(caja.getId(), null, new BigDecimal("50"), null), "operator"))
            .hasMessage("Los movimientos de la cuenta Caja requieren una caja abierta.");
        assertThat(purchase.getPaidAmount()).isEqualByComparingTo("0.00");

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(openSession.getId())).thenReturn(new BigDecimal("50.00"));
        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(caja.getId(), null, new BigDecimal("50"), "Pagó Juan"), "operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCashSessionId()).isEqualTo(openSession.getId());
        assertThat(captor.getValue().getNotes()).isEqualTo("Pagó Juan");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("50.00");
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("450.00");
        assertThat(res.payments().get(0).cashAccount()).isTrue();
        // CAJ-11: the expense and the payment count for the day of the session; the payment keeps its real time
        assertThat(captor.getValue().getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(res.payments().get(0).operationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(res.payments().get(0).paymentDate()).isEqualTo(captor.getValue().getTransactionDate());
    }

    @Test
    @DisplayName("COM-05: el pedido se bloquea antes que la caja y la cuenta (T-14)")
    void registerPayment_locksPurchaseFirst() {
        Purchase purchase = existingPurchase("300.00", "0.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));

        purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(caja.getId(), null, new BigDecimal("50"), null), "operator");

        var order = inOrder(purchaseRepository, cashSessionRepository, financialAccountRepository);
        order.verify(purchaseRepository).findByIdForUpdate(purchase.getId());
        order.verify(cashSessionRepository).findByStatusForUpdate(CashSessionStatus.ABIERTA);
        order.verify(financialAccountRepository).findActiveByIdForUpdate(caja.getId());
    }

    // ---------------------------------------------------------------- COM-07

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/La_Paz"));

    private PurchaseRequest deliveryRequest(DeliveryType type, DeliveryStatus status, LocalDate date) {
        return new PurchaseRequest(supplier.getId(), TODAY.minusDays(3), List.of(line(tea, "1", "10")), null, null,
            false, null, type, status, date, null);
    }

    @Test
    @DisplayName("COM-07: sin tipo ni estado el pedido queda en tienda y pendiente; pendiente acepta fecha estimada futura")
    void registerPurchase_deliveryDefaults() {
        PurchaseResponse res = purchaseService.registerPurchase(deliveryRequest(null, null, null), "operator");
        assertThat(res.deliveryType()).isEqualTo(DeliveryType.EN_TIENDA);
        assertThat(res.deliveryStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(res.deliveryDate()).isNull();

        PurchaseResponse expected = purchaseService.registerPurchase(
            deliveryRequest(DeliveryType.TERMINAL, DeliveryStatus.PENDIENTE, TODAY.plusDays(2)), "operator");
        assertThat(expected.deliveryType()).isEqualTo(DeliveryType.TERMINAL);
        assertThat(expected.deliveryDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    @DisplayName("COM-07: entregado sin fecha toma hoy; fecha futura o anterior al pedido se rechaza")
    void registerPurchase_deliveredDateRules() {
        PurchaseResponse res = purchaseService.registerPurchase(
            deliveryRequest(DeliveryType.EN_PUERTA, DeliveryStatus.ENTREGADO, null), "operator");
        assertThat(res.deliveryDate()).isEqualTo(TODAY);

        assertThatThrownBy(() -> purchaseService.registerPurchase(
            deliveryRequest(DeliveryType.EN_PUERTA, DeliveryStatus.ENTREGADO, TODAY.plusDays(1)), "operator"))
            .hasMessage("La fecha de entrega no puede ser futura.");
        assertThatThrownBy(() -> purchaseService.registerPurchase(
            deliveryRequest(DeliveryType.EN_PUERTA, DeliveryStatus.ENTREGADO, TODAY.minusDays(4)), "operator"))
            .hasMessage("La fecha de entrega no puede ser anterior a la fecha del pedido.");
    }

    @Test
    @DisplayName("COM-07: cambiar la entrega guarda tipo, estado, fecha y auditoría; un pedido anulado no cambia")
    void updateDelivery() {
        Purchase purchase = existingPurchase("300.00", "0.00");

        PurchaseResponse res = purchaseService.updateDelivery(purchase.getId(),
            new PurchaseDeliveryRequest(DeliveryType.TERMINAL, DeliveryStatus.ENTREGADO, TODAY), "operator");
        assertThat(res.deliveryType()).isEqualTo(DeliveryType.TERMINAL);
        assertThat(res.deliveryStatus()).isEqualTo(DeliveryStatus.ENTREGADO);
        assertThat(res.deliveryDate()).isEqualTo(TODAY);
        assertThat(purchase.getUpdatedBy()).isEqualTo("operator");
        verify(financialTransactionRepository, never()).save(any());

        PurchaseResponse back = purchaseService.updateDelivery(purchase.getId(),
            new PurchaseDeliveryRequest(DeliveryType.TERMINAL, DeliveryStatus.PENDIENTE, TODAY.plusDays(1)), "operator");
        assertThat(back.deliveryStatus()).isEqualTo(DeliveryStatus.PENDIENTE);
        assertThat(back.deliveryDate()).isEqualTo(TODAY.plusDays(1));

        purchase.setStatus(PurchaseStatus.ANULADO);
        assertThatThrownBy(() -> purchaseService.updateDelivery(purchase.getId(),
            new PurchaseDeliveryRequest(DeliveryType.EN_TIENDA, DeliveryStatus.PENDIENTE, null), "operator"))
            .hasMessage("El pedido está anulado.");
    }

    @Test
    @DisplayName("COM-07: el listado filtra por estado de entrega y por anulado; el estado de pago excluye anulados")
    void getPurchases_filtersByDeliveryAndStatus() {
        Purchase delivered = Purchase.builder().id(UUID.randomUUID()).purchaseNumber("PED-000001").supplier(supplier)
            .itemsTotal(new BigDecimal("100.00")).paidAmount(new BigDecimal("0.00"))
            .shippingCost(BigDecimal.ZERO).totalAmount(new BigDecimal("100.00"))
            .deliveryStatus(DeliveryStatus.ENTREGADO).deliveryDate(TODAY).build();
        Purchase voided = Purchase.builder().id(UUID.randomUUID()).purchaseNumber("PED-000002").supplier(supplier)
            .itemsTotal(new BigDecimal("100.00")).paidAmount(new BigDecimal("0.00"))
            .shippingCost(BigDecimal.ZERO).totalAmount(new BigDecimal("100.00"))
            .status(PurchaseStatus.ANULADO).build();
        when(purchaseRepository.search(null, null)).thenReturn(List.of(delivered, voided));

        assertThat(purchaseService.getPurchases(null, null, null, DeliveryStatus.ENTREGADO, null))
            .extracting(PurchaseResponse::purchaseNumber).containsExactly("PED-000001");
        assertThat(purchaseService.getPurchases(null, PurchaseStatus.ANULADO, null, null, null))
            .extracting(PurchaseResponse::purchaseNumber).containsExactly("PED-000002");
        assertThat(purchaseService.getPurchases(null, null, PurchasePaymentStatus.POR_PAGAR, null, null))
            .extracting(PurchaseResponse::purchaseNumber).containsExactly("PED-000001");
    }

    // ---------------------------------------------------------------- COM-08

    @Test
    @DisplayName("COM-08: los pedidos por pagar se agrupan por proveedor con su saldo y el total general")
    void getPayables_groupsBySupplier() {
        Supplier other = Supplier.builder().id(UUID.randomUUID()).name("Tés del Valle").status(SupplierStatus.ACTIVO).build();
        Purchase onAccount = existingPurchase("300.00", "100.00");
        Purchase unpaid = existingPurchase("50.00", "0.00");
        Purchase consignment = existingPurchase("80.00", "0.00");
        consignment.setSupplier(other);
        consignment.setConsignment(true);
        when(purchaseRepository.findPayables(null)).thenReturn(List.of(onAccount, unpaid, consignment));

        PayablesResponse res = purchaseService.getPayables(null);

        assertThat(res.totalBalance()).isEqualByComparingTo("330.00");
        assertThat(res.suppliers()).hasSize(2);
        PayablesResponse.SupplierPayables granel = res.suppliers().get(0);
        assertThat(granel.supplierName()).isEqualTo("Granel");
        assertThat(granel.balance()).isEqualByComparingTo("250.00");
        assertThat(granel.purchases()).extracting(PurchaseResponse::balance)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(new BigDecimal("200.00"), new BigDecimal("50.00"));
        assertThat(res.suppliers().get(1).purchases().get(0).paymentStatus()).isEqualTo(PurchasePaymentStatus.CONSIGNACION);
    }

    // ---------------------------------------------------------------- COM-09

    private static final VoidPurchaseRequest REASON = new VoidPurchaseRequest("  Pago duplicado  ");

    private FinancialTransaction purchaseExpense(Purchase purchase, FinancialAccount account, String amount, UUID sessionId) {
        FinancialTransaction tx = FinancialTransaction.builder().id(UUID.randomUUID())
            .type(FinancialTransactionType.GASTO_MANUAL).sourceAccount(account).amount(new BigDecimal(amount))
            .status(FinancialTransactionStatus.ACTIVO).purchaseId(purchase.getId()).cashSessionId(sessionId)
            .transactionDate(Instant.now()).build();
        when(financialTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        return tx;
    }

    private PurchasePayment addPayment(Purchase purchase, FinancialTransaction tx) {
        PurchasePayment payment = PurchasePayment.builder().id(UUID.randomUUID()).purchase(purchase)
            .account(tx.getSourceAccount()).amount(tx.getAmount()).paymentDate(tx.getTransactionDate())
            .financialTransactionId(tx.getId()).status(PurchasePaymentRecordStatus.ACTIVO).build();
        purchase.getPayments().add(payment);
        return payment;
    }

    private void lockAccounts(FinancialAccount... accounts) {
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(accounts));
    }

    @Test
    @DisplayName("COM-09: anular un pago desde Bisa anula su gasto, devuelve el monto y recalcula el estado")
    void voidPayment_fromAccount() {
        Purchase purchase = existingPurchase("300.00", "100.00");
        FinancialTransaction tx = purchaseExpense(purchase, bisa, "100.00", null);
        PurchasePayment payment = addPayment(purchase, tx);
        lockAccounts(bisa);

        PurchaseResponse res = purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator");

        assertThat(tx.getStatus()).isEqualTo(FinancialTransactionStatus.ANULADO);
        assertThat(tx.getVoidReason()).isEqualTo("Pago duplicado");
        assertThat(tx.getVoidedBy()).isEqualTo("operator");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1100.00");
        assertThat(payment.getStatus()).isEqualTo(PurchasePaymentRecordStatus.ANULADO);
        assertThat(payment.getVoidReason()).isEqualTo("Pago duplicado");
        assertThat(res.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.POR_PAGAR);
        verifyNoInteractions(cashSessionRepository);
    }

    @Test
    @DisplayName("COM-09: anular un pago de Caja con su caja abierta sube el efectivo esperado; con caja cerrada se rechaza")
    void voidPayment_fromCaja() {
        Purchase purchase = existingPurchase("300.00", "50.00");
        FinancialTransaction tx = purchaseExpense(purchase, caja, "50.00", openSession.getId());
        PurchasePayment payment = addPayment(purchase, tx);
        lockAccounts(caja);

        CashSession closed = CashSession.builder().id(openSession.getId()).status(CashSessionStatus.CERRADA).build();
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator"))
            .hasMessage("El pago se hizo en una caja cerrada; no se puede anular.");
        assertThat(tx.getStatus()).isEqualTo(FinancialTransactionStatus.ACTIVO);
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("500.00");

        openSession.setExpectedCash(new BigDecimal("50.00"));
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(openSession));
        purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator");

        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("550.00");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("100.00");
        var order = inOrder(purchaseRepository, cashSessionRepository, financialAccountRepository);
        order.verify(purchaseRepository).findByIdForUpdate(purchase.getId());
        order.verify(cashSessionRepository).findByIdForUpdate(openSession.getId());
        order.verify(financialAccountRepository).findAllByIdForUpdate(any());
    }

    @Test
    @DisplayName("COM-09: anular un pago exige motivo, rechaza uno ya anulado, ajeno o de un pedido anulado")
    void voidPayment_rejections() {
        Purchase purchase = existingPurchase("300.00", "100.00");
        PurchasePayment payment = addPayment(purchase, purchaseExpense(purchase, bisa, "100.00", null));
        lockAccounts(bisa);

        assertThatThrownBy(() -> purchaseService.voidPayment(purchase.getId(), payment.getId(), new VoidPurchaseRequest("  "), "operator"))
            .hasMessage("El motivo de anulación es obligatorio.");
        assertThatThrownBy(() -> purchaseService.voidPayment(purchase.getId(), payment.getId(), null, "operator"))
            .hasMessage("El motivo de anulación es obligatorio.");
        assertThatThrownBy(() -> purchaseService.voidPayment(purchase.getId(), UUID.randomUUID(), REASON, "operator"))
            .isInstanceOf(com.akhana.akhana_admin.exception.ResourceNotFoundException.class);

        purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator");
        assertThatThrownBy(() -> purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator"))
            .hasMessage("El pago ya está anulado.");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1100.00");

        purchase.setStatus(PurchaseStatus.ANULADO);
        assertThatThrownBy(() -> purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator"))
            .hasMessage("El pedido está anulado.");
    }

    @Test
    @DisplayName("COM-09: anular un pedido anula sus pagos activos y el envío, y devuelve cada monto a su cuenta")
    void voidPurchase_revertsPaymentsAndShipping() {
        Purchase purchase = existingPurchase("300.00", "150.00");
        purchase.setShippingCost(new BigDecimal("25.00"));
        FinancialTransaction shipping = purchaseExpense(purchase, bisa, "25.00", null);
        FinancialTransaction bisaPayment = purchaseExpense(purchase, bisa, "100.00", null);
        FinancialTransaction cajaPayment = purchaseExpense(purchase, caja, "50.00", openSession.getId());
        FinancialTransaction alreadyVoided = purchaseExpense(purchase, bisa, "30.00", null);
        alreadyVoided.setStatus(FinancialTransactionStatus.ANULADO);
        addPayment(purchase, bisaPayment);
        addPayment(purchase, cajaPayment);
        PurchasePayment voidedPayment = addPayment(purchase, alreadyVoided);
        voidedPayment.setStatus(PurchasePaymentRecordStatus.ANULADO);
        voidedPayment.setVoidReason("Antes");
        when(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.getId()))
            .thenReturn(List.of(shipping, bisaPayment, cajaPayment, alreadyVoided));
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(openSession));
        lockAccounts(bisa, caja);

        PurchaseResponse res = purchaseService.voidPurchase(purchase.getId(), REASON, "operator");

        assertThat(res.status()).isEqualTo(PurchaseStatus.ANULADO);
        assertThat(res.voidReason()).isEqualTo("Pago duplicado");
        assertThat(res.voidedBy()).isEqualTo("operator");
        assertThat(res.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1125.00");
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("550.00");
        assertThat(List.of(shipping, bisaPayment, cajaPayment)).allMatch(tx -> tx.getStatus() == FinancialTransactionStatus.ANULADO);
        assertThat(purchase.getPayments()).allMatch(p -> p.getStatus() == PurchasePaymentRecordStatus.ANULADO);
        assertThat(voidedPayment.getVoidReason()).isEqualTo("Antes");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("100.00");

        assertThatThrownBy(() -> purchaseService.voidPurchase(purchase.getId(), REASON, "operator"))
            .hasMessage("El pedido ya está anulado.");
    }

    @Test
    @DisplayName("COM-09: un pedido con movimientos de una caja cerrada no se anula y nada cambia")
    void voidPurchase_rejectsClosedSession() {
        Purchase purchase = existingPurchase("300.00", "150.00");
        FinancialTransaction bisaPayment = purchaseExpense(purchase, bisa, "100.00", null);
        FinancialTransaction cajaPayment = purchaseExpense(purchase, caja, "50.00", UUID.randomUUID());
        addPayment(purchase, bisaPayment);
        addPayment(purchase, cajaPayment);
        when(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.getId()))
            .thenReturn(List.of(bisaPayment, cajaPayment));
        when(cashSessionRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());
        lockAccounts(bisa, caja);

        assertThatThrownBy(() -> purchaseService.voidPurchase(purchase.getId(), REASON, "operator"))
            .hasMessage("El pedido tiene movimientos de una caja cerrada; no se puede anular.");
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.REGISTRADO);
        assertThat(bisaPayment.getStatus()).isEqualTo(FinancialTransactionStatus.ACTIVO);
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("COM-09: anular un pedido no revierte el costo ni el precio del producto")
    void voidPurchase_keepsProductCost() {
        Purchase purchase = existingPurchase("123.00", "0.00");
        purchase.getItems().add(PurchaseItem.builder().id(UUID.randomUUID()).purchase(purchase).product(tea)
            .productName(tea.getName()).quantity(BigDecimal.TEN).unitCost(new BigDecimal("12.30"))
            .subtotal(new BigDecimal("123.00")).previousUnitCost(new BigDecimal("10.00")).costUpdated(true).build());
        tea.setBuyPrice(new BigDecimal("12.30"));
        when(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.getId())).thenReturn(List.of());

        purchaseService.voidPurchase(purchase.getId(), REASON, "operator");

        assertThat(tea.getBuyPrice()).isEqualByComparingTo("12.30");
        verify(productRepository, never()).save(any());
        verify(productPriceChangeRepository, never()).save(any());
        verify(financialAccountRepository, never()).findAllByIdForUpdate(any());
    }

    // ---------------------------------------------------------------- DEU-02

    private PurchasePaymentRequest byMami(String amount) {
        return new PurchasePaymentRequest(null, mami.getId(), new BigDecimal(amount), null);
    }

    private PurchasePayment addMamiPayment(Purchase purchase, String amount) {
        PurchasePayment payment = PurchasePayment.builder().id(UUID.randomUUID()).purchase(purchase).creditor(mami)
            .amount(new BigDecimal(amount)).paymentDate(Instant.now()).status(PurchasePaymentRecordStatus.ACTIVO).build();
        purchase.getPayments().add(payment);
        return payment;
    }

    @Test
    @DisplayName("DEU-02: Mami paga Bs 200 de un pedido de Bs 300: queda a cuenta, sin movimiento ni cambio de cuentas")
    void registerPayment_byPerson() {
        Purchase purchase = existingPurchase("300.00", "0.00");

        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(), byMami("200"), "operator");

        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.A_CUENTA);
        assertThat(res.balance()).isEqualByComparingTo("100.00");
        assertThat(res.payments()).hasSize(1);
        assertThat(res.payments().get(0).creditorName()).isEqualTo("Mami");
        assertThat(res.payments().get(0).accountId()).isNull();
        assertThat(res.payments().get(0).financialTransactionId()).isNull();
        // CAJ-11: without a movement it counts for the day it is recorded
        assertThat(res.payments().get(0).operationDate()).isEqualTo(StoreFormat.storeDay(res.payments().get(0).paymentDate()));
        verify(financialTransactionRepository, never()).save(any());
        verify(financialAccountRepository, never()).findActiveByIdForUpdate(any());
        verifyNoInteractions(cashSessionRepository);

        var order = inOrder(purchaseRepository, creditorRepository);
        order.verify(purchaseRepository).findByIdForUpdate(purchase.getId());
        order.verify(creditorRepository).findByIdForUpdate(mami.getId());

        res = purchaseService.registerPayment(purchase.getId(), byMami("100"), "operator");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);
    }

    @Test
    @DisplayName("DEU-02: pago por persona mayor al saldo, persona inactiva u origen doble o vacío se rechazan")
    void registerPayment_byPersonRejections() {
        Purchase purchase = existingPurchase("300.00", "100.00");

        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(), byMami("200.01"), "operator"))
            .hasMessage("El pago supera el saldo pendiente del pedido (Bs 200,00).");
        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(bisa.getId(), mami.getId(), new BigDecimal("10"), null), "operator"))
            .hasMessage("Elija una cuenta o una persona como origen del pago.");
        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(),
            new PurchasePaymentRequest(null, null, new BigDecimal("10"), null), "operator"))
            .hasMessage("Elija una cuenta o una persona como origen del pago.");

        mami.setStatus(CreditorStatus.INACTIVO);
        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(), byMami("10"), "operator"))
            .hasMessage("La persona 'Mami' no está activa.");
        mami.setStatus(CreditorStatus.ELIMINADO);
        assertThatThrownBy(() -> purchaseService.registerPayment(purchase.getId(), byMami("10"), "operator"))
            .hasMessage("La persona 'Mami' no está activa.");
        assertThat(purchase.getPayments()).isEmpty();
        assertThat(purchase.getPaidAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("DEU-02: un pedido con pagos de Bisa y de Mami muestra cada pago con su origen")
    void registerPayment_mixedSources() {
        Purchase purchase = existingPurchase("300.00", "0.00");

        purchaseService.registerPayment(purchase.getId(), new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("100"), null), "operator");
        PurchaseResponse res = purchaseService.registerPayment(purchase.getId(), byMami("200"), "operator");

        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);
        assertThat(res.payments()).extracting(p -> p.accountName() != null ? p.accountName() : p.creditorName())
            .containsExactly("Bisa", "Mami");
    }

    @Test
    @DisplayName("DEU-02: el pago inicial de un pedido puede hacerlo una persona, en la misma transacción")
    void registerPurchase_initialPaymentByPerson() {
        PurchaseRequest req = new PurchaseRequest(supplier.getId(), null, List.of(line(tea, "10", "10")), null, null,
            false, null, null, null, null, byMami("40"));

        PurchaseResponse res = purchaseService.registerPurchase(req, "operator");

        assertThat(res.paidAmount()).isEqualByComparingTo("40.00");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.A_CUENTA);
        assertThat(res.payments()).extracting(PurchasePaymentResponse::creditorName).containsExactly("Mami");
        verify(financialTransactionRepository, never()).save(any());

        PurchaseRequest fromBisa = new PurchaseRequest(supplier.getId(), null, List.of(line(tea, "10", "10")), null, null,
            false, null, null, null, null, new PurchasePaymentRequest(bisa.getId(), null, new BigDecimal("100"), null));
        res = purchaseService.registerPurchase(fromBisa, "operator");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("900.00");

        PurchaseRequest tooMuch = new PurchaseRequest(supplier.getId(), null, List.of(line(tea, "1", "10")), null, null,
            false, null, null, null, null, byMami("11"));
        assertThatThrownBy(() -> purchaseService.registerPurchase(tooMuch, "operator"))
            .hasMessage("El pago supera el saldo pendiente del pedido (Bs 10,00).");
    }

    @Test
    @DisplayName("DEU-02: anular un pago de Mami baja la deuda sin movimiento; si ya se le devolvió, se rechaza")
    void voidPayment_byPerson() {
        Purchase purchase = existingPurchase("300.00", "200.00");
        PurchasePayment payment = addMamiPayment(purchase, "200.00");

        when(creditorRepository.computeDebt(mami.getId())).thenReturn(new BigDecimal("50.00"));
        assertThatThrownBy(() -> purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator"))
            .hasMessage("Ya se devolvió a Mami parte de este pago; anule primero la devolución.");
        assertThat(payment.getStatus()).isEqualTo(PurchasePaymentRecordStatus.ACTIVO);

        when(creditorRepository.computeDebt(mami.getId())).thenReturn(new BigDecimal("200.00"));
        PurchaseResponse res = purchaseService.voidPayment(purchase.getId(), payment.getId(), REASON, "operator");

        assertThat(payment.getStatus()).isEqualTo(PurchasePaymentRecordStatus.ANULADO);
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.POR_PAGAR);
        verify(financialTransactionRepository, never()).findById(any());
        verify(financialAccountRepository, never()).findAllByIdForUpdate(any());
        var order = inOrder(purchaseRepository, creditorRepository);
        order.verify(purchaseRepository).findByIdForUpdate(purchase.getId());
        order.verify(creditorRepository).findByIdForUpdate(mami.getId());
    }

    @Test
    @DisplayName("DEU-02: anular un pedido con pagos de Mami y de Bisa; si la deuda quedaría negativa no se anula nada")
    void voidPurchase_withPersonPayments() {
        Purchase purchase = existingPurchase("300.00", "300.00");
        PurchasePayment mamiPayment = addMamiPayment(purchase, "200.00");
        FinancialTransaction bisaTx = purchaseExpense(purchase, bisa, "100.00", null);
        PurchasePayment bisaPayment = addPayment(purchase, bisaTx);
        when(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.getId())).thenReturn(List.of(bisaTx));
        lockAccounts(bisa);

        when(creditorRepository.computeDebt(mami.getId())).thenReturn(new BigDecimal("199.99"));
        assertThatThrownBy(() -> purchaseService.voidPurchase(purchase.getId(), REASON, "operator"))
            .hasMessage("Ya se devolvió a Mami parte de este pago; anule primero la devolución.");
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.REGISTRADO);
        assertThat(bisaTx.getStatus()).isEqualTo(FinancialTransactionStatus.ACTIVO);
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1000.00");

        when(creditorRepository.computeDebt(mami.getId())).thenReturn(new BigDecimal("200.00"));
        purchaseService.voidPurchase(purchase.getId(), REASON, "operator");

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.ANULADO);
        assertThat(mamiPayment.getStatus()).isEqualTo(PurchasePaymentRecordStatus.ANULADO);
        assertThat(bisaPayment.getStatus()).isEqualTo(PurchasePaymentRecordStatus.ANULADO);
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1100.00");
        var order = inOrder(purchaseRepository, creditorRepository, financialAccountRepository);
        order.verify(purchaseRepository).findByIdForUpdate(purchase.getId());
        order.verify(creditorRepository).findAllByIdForUpdate(any());
        order.verify(financialAccountRepository).findAllByIdForUpdate(any());
    }

    // ---------------------------------------------------------------- COM-11, COM-12

    private PurchaseItem nutsLine;
    private PurchaseItem teaLine;

    // Nueces 1,5 kg a Bs 40 (60) + Té verde 24 a Bs 10 (240) = Bs 300, delivered, shipping Bs 0
    private Purchase editablePurchase(String paid) {
        Purchase purchase = existingPurchase("300.00", paid);
        purchase.setDeliveryStatus(DeliveryStatus.ENTREGADO);
        purchase.setDeliveryDate(purchase.getPurchaseDate());
        nutsLine = PurchaseItem.builder().id(UUID.randomUUID()).purchase(purchase).product(nuts).productName("Nueces")
            .quantity(new BigDecimal("1.500")).unitCost(new BigDecimal("40.00")).subtotal(new BigDecimal("60.00"))
            .previousUnitCost(new BigDecimal("40.00")).build();
        teaLine = PurchaseItem.builder().id(UUID.randomUUID()).purchase(purchase).product(tea).productName("Té verde")
            .quantity(new BigDecimal("24.000")).unitCost(new BigDecimal("10.00")).subtotal(new BigDecimal("240.00"))
            .previousUnitCost(new BigDecimal("10.00")).build();
        purchase.getItems().addAll(List.of(nutsLine, teaLine));
        return purchase;
    }

    private PurchaseItemRequest kept(PurchaseItem item, String quantity, String unitCost) {
        return new PurchaseItemRequest(item.getProduct().getId(), new BigDecimal(quantity), new BigDecimal(unitCost),
            false, null, item.getId());
    }

    private PurchaseUpdateRequest edit(Purchase purchase, List<PurchaseItemRequest> items, String shipping,
                                       UUID shippingAccountId, String reason) {
        return new PurchaseUpdateRequest(purchase.getSupplier().getId(), purchase.getPurchaseDate(), items,
            new BigDecimal(shipping), shippingAccountId, false, null, purchase.getDeliveryType(),
            purchase.getDeliveryStatus(), purchase.getDeliveryDate(), reason);
    }

    @Test
    @DisplayName("COM-11: agregar lo que llegó sin pedirse, cambiar cantidad y quitar lo que faltó recalcula totales y audita")
    void updatePurchase_addsChangesAndRemovesLines() {
        Purchase purchase = editablePurchase("0.00");
        Product chia = Product.builder().id(UUID.randomUUID()).code("CH-1").name("Barra Chia").saleUnit(SaleUnit.UNIT)
            .buyPrice(new BigDecimal("5.00")).sellPrice(new BigDecimal("8.00")).status(ProductStatus.ACTIVO).build();
        when(productRepository.findById(chia.getId())).thenReturn(Optional.of(chia));

        PurchaseResponse res = purchaseService.updatePurchase(purchase.getId(), edit(purchase, List.of(
            kept(teaLine, "20", "10.00"),
            new PurchaseItemRequest(chia.getId(), new BigDecimal("2"), new BigDecimal("5.00"), false, null)),
            "0", null, "  Faltaron las nueces y llegaron 2 barras  "), "operator");

        assertThat(nutsLine.isRemoved()).isTrue();
        assertThat(nutsLine.getRemovedBy()).isEqualTo("operator");
        assertThat(res.items()).extracting(i -> i.productName()).containsExactly("Té verde", "Barra Chia");
        assertThat(res.items().get(1).previousUnitCost()).isEqualByComparingTo("5.00");
        assertThat(teaLine.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(res.itemsTotal()).isEqualByComparingTo("210.00");
        assertThat(res.totalAmount()).isEqualByComparingTo("210.00");
        assertThat(res.balance()).isEqualByComparingTo("210.00");
        assertThat(res.updatedBy()).isEqualTo("operator");
        assertThat(res.revisions()).hasSize(1);
        assertThat(res.revisions().get(0).reason()).isEqualTo("Faltaron las nueces y llegaron 2 barras");
        assertThat(res.revisions().get(0).revisedBy()).isEqualTo("operator");
        assertThat(res.revisions().get(0).changes()).isEqualTo("Cantidad de Té verde: 24 → 20 · "
            + "Línea agregada: Barra Chia × 2 a Bs 5,00 · Línea quitada: Nueces · Total: Bs 300,00 → Bs 210,00");
        verifyNoInteractions(financialAccountRepository);
    }

    @Test
    @DisplayName("COM-11: un pedido pagado al que se agrega una línea queda A cuenta con el saldo nuevo")
    void updatePurchase_paidPurchaseGetsBalance() {
        Purchase purchase = editablePurchase("300.00");

        PurchaseResponse res = purchaseService.updatePurchase(purchase.getId(), edit(purchase, List.of(
            kept(nutsLine, "1.5", "40"), kept(teaLine, "26", "10")), "0", null, "Llegaron 2 tés más"), "operator");

        assertThat(res.itemsTotal()).isEqualByComparingTo("320.00");
        assertThat(res.paidAmount()).isEqualByComparingTo("300.00");
        assertThat(res.balance()).isEqualByComparingTo("20.00");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.A_CUENTA);
    }

    @Test
    @DisplayName("COM-12: el total no queda menor a lo pagado (también lo pagado por una persona); igual se acepta")
    void updatePurchase_totalBelowPaidRejected() {
        Purchase purchase = editablePurchase("300.00");
        purchase.getPayments().add(PurchasePayment.builder().id(UUID.randomUUID()).purchase(purchase).creditor(mami)
            .amount(new BigDecimal("300.00")).status(PurchasePaymentRecordStatus.ACTIVO).build());

        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(), edit(purchase,
            List.of(kept(teaLine, "24", "10")), "0", null, "Faltaron las nueces"), "operator"))
            .hasMessage("El total del pedido (Bs 240,00) no puede ser menor a lo pagado (Bs 300,00); anule primero el pago.");
        verify(purchaseRepository, never()).save(any());

        // A rejected edit rolls back; the unit test starts again from the same purchase
        Purchase again = editablePurchase("300.00");
        PurchaseResponse res = purchaseService.updatePurchase(again.getId(), edit(again, List.of(
            kept(nutsLine, "1", "60"), kept(teaLine, "24", "10")), "0", null, "Otro costo de nueces"), "operator");
        assertThat(res.paymentStatus()).isEqualTo(PurchasePaymentStatus.PAGADO);
        assertThat(res.revisions().get(0).changes()).isEqualTo(
            "Cantidad de Nueces: 1,500 → 1 · Costo de Nueces: Bs 40,00 → Bs 60,00");
    }

    @Test
    @DisplayName("COM-11: motivo obligatorio, sin cambios, producto cambiado, línea repetida, anulado e histórico se rechazan")
    void updatePurchase_rejections() {
        Purchase purchase = editablePurchase("0.00");
        List<PurchaseItemRequest> same = List.of(kept(nutsLine, "1.5", "40"), kept(teaLine, "24", "10"));

        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(), edit(purchase, same, "0", null, "  "), "op"))
            .hasMessage("El motivo del cambio es obligatorio.");
        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(), edit(purchase, same, "0", null, "Nada"), "op"))
            .hasMessage("No hay cambios para guardar.");
        PurchaseItemRequest otherProduct = new PurchaseItemRequest(tea.getId(), BigDecimal.ONE, BigDecimal.TEN, false, null,
            nutsLine.getId());
        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(),
            edit(purchase, List.of(otherProduct), "0", null, "Cambio"), "op"))
            .hasMessage("No se cambia el producto de una línea: quítela y agregue otra.");
        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(),
            edit(purchase, List.of(kept(teaLine, "1", "10"), kept(teaLine, "2", "10")), "0", null, "Cambio"), "op"))
            .hasMessage("La línea de 'Té verde' está repetida.");

        purchase.setHistoricalImportId(UUID.randomUUID());
        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(),
            edit(purchase, List.of(kept(teaLine, "1", "10")), "0", null, "Cambio"), "op"))
            .hasMessage("El pedido es histórico (migrado de la planilla): solo se le pueden registrar pagos.");
        purchase.setStatus(PurchaseStatus.ANULADO);
        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(),
            edit(purchase, List.of(kept(teaLine, "1", "10")), "0", null, "Cambio"), "op"))
            .hasMessage("El pedido está anulado.");
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("COM-12: cambiar el envío anula el gasto de Bisa, devuelve el monto y registra el nuevo en Caja")
    void updatePurchase_redoesShipping() {
        Purchase purchase = editablePurchase("0.00");
        purchase.setShippingCost(new BigDecimal("25.00"));
        purchase.setTotalAmount(new BigDecimal("325.00"));
        FinancialTransaction oldShipping = purchaseExpense(purchase, bisa, "25.00", null);
        when(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.getId()))
            .thenReturn(List.of(oldShipping));
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        lockAccounts(bisa, caja);

        PurchaseResponse res = purchaseService.updatePurchase(purchase.getId(), edit(purchase, List.of(
            kept(nutsLine, "1.5", "40"), kept(teaLine, "24", "10")), "30.00", caja.getId(), "El envío costó 30"), "operator");

        assertThat(oldShipping.getStatus()).isEqualTo(FinancialTransactionStatus.ANULADO);
        assertThat(oldShipping.getVoidReason()).isEqualTo("El envío costó 30");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1025.00");
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("470.00");
        ArgumentCaptor<FinancialTransaction> saved = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository, atLeastOnce()).save(saved.capture());
        FinancialTransaction newShipping = saved.getAllValues().stream()
            .filter(tx -> tx.getStatus() == FinancialTransactionStatus.ACTIVO).findFirst().orElseThrow();
        assertThat(newShipping.getAmount()).isEqualByComparingTo("30.00");
        assertThat(newShipping.getCategory()).isEqualTo(transporte);
        assertThat(newShipping.getCashSessionId()).isEqualTo(openSession.getId());
        assertThat(newShipping.getDescription()).isEqualTo("Envío pedido PED-000001");
        assertThat(res.shippingCost()).isEqualByComparingTo("30.00");
        assertThat(res.totalAmount()).isEqualByComparingTo("330.00");
        assertThat(res.itemsTotal()).isEqualByComparingTo("300.00");
        assertThat(res.revisions().get(0).changes()).isEqualTo("Envío: Bs 25,00 (Bisa) → Bs 30,00 (Caja)");
        var order = inOrder(purchaseRepository, cashSessionRepository, financialAccountRepository);
        order.verify(purchaseRepository).findByIdForUpdate(purchase.getId());
        order.verify(cashSessionRepository).findByStatusForUpdate(CashSessionStatus.ABIERTA);
        order.verify(financialAccountRepository).findAllByIdForUpdate(Set.of(bisa.getId(), caja.getId()));
    }

    @Test
    @DisplayName("COM-12: el envío pagado en una caja cerrada no se cambia; sin cambio de envío su gasto no se toca")
    void updatePurchase_shippingInClosedSession() {
        Purchase purchase = editablePurchase("0.00");
        purchase.setShippingCost(new BigDecimal("25.00"));
        FinancialTransaction oldShipping = purchaseExpense(purchase, caja, "25.00", openSession.getId());
        when(financialTransactionRepository.findByPurchaseIdOrderByTransactionDateAsc(purchase.getId()))
            .thenReturn(List.of(oldShipping));
        CashSession closed = CashSession.builder().id(openSession.getId()).status(CashSessionStatus.CERRADA).build();
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> purchaseService.updatePurchase(purchase.getId(), edit(purchase, List.of(
            kept(nutsLine, "1.5", "40"), kept(teaLine, "24", "10")), "0", null, "Sin envío"), "operator"))
            .hasMessage("El envío se pagó en una caja cerrada; no se puede cambiar.");
        assertThat(oldShipping.getStatus()).isEqualTo(FinancialTransactionStatus.ACTIVO);

        purchaseService.updatePurchase(purchase.getId(), edit(purchase, List.of(
            kept(nutsLine, "1.5", "40"), kept(teaLine, "25", "10")), "25.00", caja.getId(), "Llegó 1 té más"), "operator");
        assertThat(oldShipping.getStatus()).isEqualTo(FinancialTransactionStatus.ACTIVO);
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("COM-11: el costo distinto confirmado al editar actualiza el producto con el precio elegido (COM-03)")
    void updatePurchase_updatesProductCost() {
        Purchase purchase = editablePurchase("0.00");
        PurchaseItemRequest teaAt12 = new PurchaseItemRequest(tea.getId(), new BigDecimal("24"), new BigDecimal("12.00"),
            true, new BigDecimal("18.00"), teaLine.getId());

        PurchaseResponse res = purchaseService.updatePurchase(purchase.getId(), edit(purchase, List.of(
            kept(nutsLine, "1.5", "40"), teaAt12), "0", null, "Llegó a otro costo"), "operator");

        assertThat(tea.getBuyPrice()).isEqualByComparingTo("12.00");
        assertThat(tea.getSellPrice()).isEqualByComparingTo("18.00");
        assertThat(teaLine.getCostUpdated()).isTrue();
        ArgumentCaptor<ProductPriceChange> change = ArgumentCaptor.forClass(ProductPriceChange.class);
        verify(productPriceChangeRepository).save(change.capture());
        assertThat(change.getValue().getPurchaseId()).isEqualTo(purchase.getId());
        assertThat(change.getValue().getSource()).isEqualTo(PriceChangeSource.PURCHASE);
        assertThat(res.itemsTotal()).isEqualByComparingTo("348.00");
        assertThat(res.revisions().get(0).changes()).contains("Costo de Té verde: Bs 10,00 → Bs 12,00",
            "Producto Té verde: costo Bs 10,00 → Bs 12,00, precio Bs 15,00 → Bs 18,00");
    }
}
