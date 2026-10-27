package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleItemRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.ProductPresentationServiceImpl;
import com.akhana.akhana_admin.service.impl.SaleServiceImpl;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Free lines, unidentified sales and their assignment, third-party sales (POS-11 to POS-13). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaleServiceFreeLineTest {

    @Mock private SaleRepository saleRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private FinancialAccountRepository financialAccountRepository;
    @Mock private FinancialCategoryRepository financialCategoryRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductPresentationRepository presentationRepository;
    @Mock private ProductPriceChangeRepository priceChangeRepository;
    @Mock private SaleItemRepository saleItemRepository;
    @Mock private CreditorRepository creditorRepository;

    private SaleServiceImpl saleService;
    private CashSession session;
    private Product normal;
    private Product unidentified;
    private Product mamiProduct;
    private Creditor mami;
    private FinancialAccount caja;

    @BeforeEach
    void setUp() {
        saleService = new SaleServiceImpl(saleRepository, cashSessionRepository, productRepository, financialAccountRepository,
            financialCategoryRepository, financialTransactionRepository, new ExpectedCashCalculator(financialTransactionRepository),
            customerRepository, new ProductPresentationServiceImpl(presentationRepository, priceChangeRepository),
            saleItemRepository, creditorRepository);
        session = CashSession.builder().id(UUID.randomUUID()).sessionNumber(1L).status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00")).expectedCash(new BigDecimal("100.00"))
            .totalSalesCash(BigDecimal.ZERO).totalSalesQr(BigDecimal.ZERO).totalSales(BigDecimal.ZERO).build();
        normal = Product.builder().id(UUID.randomUUID()).code("GRA-250").name("Granola coco 250 g")
            .buyPrice(new BigDecimal("15.50")).sellPrice(new BigDecimal("25.00")).status(ProductStatus.ACTIVO).build();
        unidentified = Product.builder().id(UUID.randomUUID()).code("SIN-IDENT").name("Producto sin identificar")
            .buyPrice(BigDecimal.ZERO).sellPrice(BigDecimal.ZERO).status(ProductStatus.ACTIVO)
            .specialType(ProductSpecialType.UNIDENTIFIED).build();
        mami = Creditor.builder().id(UUID.randomUUID()).name("Mami").status(CreditorStatus.ACTIVO).build();
        mamiProduct = Product.builder().id(UUID.randomUUID()).code("MAMI").name("Producto de Mami")
            .buyPrice(BigDecimal.ZERO).sellPrice(BigDecimal.ZERO).status(ProductStatus.ACTIVO)
            .specialType(ProductSpecialType.THIRD_PARTY).ownerCreditor(mami).build();
        caja = FinancialAccount.builder().id(UUID.randomUUID()).name("Caja").currentBalance(new BigDecimal("100.00"))
            .isCashAccount(true).build();

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(session));
        when(financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()).thenReturn(Optional.of(
            FinancialCategory.builder().id(UUID.randomUUID()).name("Venta").type(FinancialCategoryType.INGRESO).build()));
        when(financialAccountRepository.findSaleAccountsForUpdate()).thenReturn(List.of(caja));
        for (Product p : List.of(normal, unidentified, mamiProduct)) {
            when(productRepository.findById(p.getId())).thenReturn(Optional.of(p));
        }
        when(saleRepository.nextSaleNumber()).thenReturn(7L);
        when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
    }

    private static SaleItemRequest free(Product product, String quantity, String name, String price) {
        return new SaleItemRequest(product.getId(), new BigDecimal(quantity), null, null, name,
            price != null ? new BigDecimal(price) : null);
    }

    private static SaleDetailRequest cashSale(BigDecimal globalDiscount, SaleItemRequest... items) {
        return new SaleDetailRequest(List.of(items), globalDiscount, PaymentMethod.EFECTIVO, null, null, null, null);
    }

    private Sale registeredSale() {
        ArgumentCaptor<Sale> captor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("POS-12: la línea sin identificar guarda nombre y precio, queda por asignar con costo 0 y entra a caja")
    void unidentifiedLineWaitsForItsProduct() {
        SaleResponse response = saleService.registerSale(cashSale(null, free(unidentified, "2", " Granola de coco ", "25")), "operador");

        SaleItem line = registeredSale().getItems().getFirst();
        assertThat(line.getProductName()).isEqualTo("Granola de coco");
        assertThat(line.getFreeName()).isEqualTo("Granola de coco");
        assertThat(line.getFreeLineType()).isEqualTo(ProductSpecialType.UNIDENTIFIED);
        assertThat(line.getAssignmentStatus()).isEqualTo(SaleItemAssignmentStatus.POR_ASIGNAR);
        assertThat(line.getUnitCost()).isEqualByComparingTo("0");
        assertThat(line.getSubtotal()).isEqualByComparingTo("50.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("150.00");
        assertThat(session.getTotalSales()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("POS-13: la línea de Mami cuesta su precio y lleva a Mami como dueña")
    void thirdPartyLineCostsItsPrice() {
        saleService.registerSale(cashSale(null, free(mamiProduct, "2", "Mermelada de Mami", "30.00"),
            new SaleItemRequest(normal.getId(), BigDecimal.ONE, null)), "operador");

        Sale sale = registeredSale();
        SaleItem line = sale.getItems().getFirst();
        assertThat(line.getUnitCost()).isEqualByComparingTo("30.00");
        assertThat(line.getCreditor()).isSameAs(mami);
        assertThat(line.getAssignmentStatus()).isNull();
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("85.00");
        assertThat(sale.getSubtotalAmount()).isEqualByComparingTo("85.00");
    }

    @Test
    @DisplayName("POS-11: sin nombre, con precio 0 o con 3 decimales se rechaza")
    void freeLineNeedsNameAndPrice() {
        assertThatThrownBy(() -> saleService.registerSale(cashSale(null, free(unidentified, "1", "  ", "10")), "op"))
            .hasMessage("Escriba el nombre de la línea libre.");
        assertThatThrownBy(() -> saleService.registerSale(cashSale(null, free(unidentified, "1", "Té", "0")), "op"))
            .hasMessage("El precio de 'Té' debe ser mayor a 0, con hasta 2 decimales.");
        assertThatThrownBy(() -> saleService.registerSale(cashSale(null, free(unidentified, "1", "Té", "1.005")), "op"))
            .hasMessage("El precio de 'Té' debe ser mayor a 0, con hasta 2 decimales.");
        assertThatThrownBy(() -> saleService.registerSale(cashSale(null, free(unidentified, "0.5", "Té", "10")), "op"))
            .hasMessage("La cantidad de 'Té' debe ser entera: se vende por unidad.");
        verify(saleRepository, never()).save(any());
    }

    @Test
    @DisplayName("POS-11: un producto normal no lleva nombre ni precio escritos")
    void normalProductRejectsFreeNameAndPrice() {
        assertThatThrownBy(() -> saleService.registerSale(cashSale(null, free(normal, "1", "Otro", "10")), "op"))
            .hasMessage("Solo un producto especial lleva nombre y precio escritos.");
    }

    @Test
    @DisplayName("POS-11: líneas libres sin descuentos ni en venta al costo")
    void freeLinesWithoutDiscountsOrAtCost() {
        SaleItemRequest discounted = new SaleItemRequest(unidentified.getId(), BigDecimal.ONE, new BigDecimal("1.00"),
            null, "Té", new BigDecimal("10"));
        assertThatThrownBy(() -> saleService.registerSale(cashSale(null, discounted), "op"))
            .hasMessage("Una línea libre no admite descuentos.");
        assertThatThrownBy(() -> saleService.registerSale(cashSale(new BigDecimal("1.00"), free(unidentified, "1", "Té", "10")), "op"))
            .hasMessage("Una venta con líneas libres no admite descuento general.");
        SaleDetailRequest atCost = new SaleDetailRequest(List.of(free(mamiProduct, "1", "Té", "10")), null,
            PaymentMethod.EFECTIVO, null, null, null, null, SaleType.AL_COSTO, null, null);
        assertThatThrownBy(() -> saleService.registerSale(atCost, "op"))
            .hasMessage("Una venta al costo no admite líneas libres.");
    }

    @Test
    @DisplayName("POS-13: anular la venta se rechaza si la deuda con Mami quedaría negativa")
    void voidRejectedWhenAlreadyRepaid() {
        Sale sale = saleWithLine(SaleItem.builder().id(UUID.randomUUID()).creditor(mami)
            .freeLineType(ProductSpecialType.THIRD_PARTY).subtotal(new BigDecimal("60.00")).build());
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        when(creditorRepository.findAllByIdForUpdate(any())).thenReturn(List.of(mami));
        when(creditorRepository.computeDebt(mami.getId())).thenReturn(new BigDecimal("40.00"));

        assertThatThrownBy(() -> saleService.voidSale(sale.getId(), new VoidSaleRequest("Error"), "op"))
            .hasMessage("Ya se devolvió a Mami parte de esta venta; anule primero la devolución.");
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETADA);

        when(creditorRepository.computeDebt(mami.getId())).thenReturn(new BigDecimal("60.00"));
        saleService.voidSale(sale.getId(), new VoidSaleRequest("Error"), "op");
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.ANULADA);
    }

    @Test
    @DisplayName("POS-12: asignar toma el producto real y su costo actual sin tocar montos ni caja")
    void assignTakesTheRealProduct() {
        SaleItem line = pendingLine();
        Sale sale = saleWithLine(line);

        saleService.assignItem(sale.getId(), line.getId(), normal.getId(), "admin");

        verify(saleItemRepository).assign(eq(line.getId()), eq(normal), eq("GRA-250"), eq("Granola coco 250 g"),
            eq(new BigDecimal("15.50")), eq(SaleItemAssignmentStatus.ASIGNADO), eq("admin"), any());
        verifyNoInteractions(financialTransactionRepository);
        verify(cashSessionRepository, never()).save(any());

        line.setAssignmentStatus(SaleItemAssignmentStatus.ASIGNADO);
        assertThatThrownBy(() -> saleService.assignItem(sale.getId(), line.getId(), normal.getId(), "admin"))
            .hasMessage("La línea ya fue asignada.");
    }

    @Test
    @DisplayName("POS-12: no se asigna a un producto especial, inactivo o por kilo, ni en una venta anulada")
    void assignRejectsInvalidTargets() {
        SaleItem line = pendingLine();
        Sale sale = saleWithLine(line);

        assertThatThrownBy(() -> saleService.assignItem(sale.getId(), line.getId(), mamiProduct.getId(), "admin"))
            .hasMessage("'Producto de Mami' es un producto especial: solo se vende en el POS con nombre y precio.");
        normal.setSaleUnit(SaleUnit.KILOGRAM);
        assertThatThrownBy(() -> saleService.assignItem(sale.getId(), line.getId(), normal.getId(), "admin"))
            .hasMessage("Elija un producto activo que se venda por unidad.");
        sale.setStatus(SaleStatus.ANULADA);
        assertThatThrownBy(() -> saleService.closeUnidentified(sale.getId(), line.getId(), "admin"))
            .hasMessage("Una venta anulada no se asigna.");
        verify(saleItemRepository, never()).assign(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POS-12: sin identificar definitivo deja costo 0 y el producto especial")
    void closeUnidentifiedKeepsCostZero() {
        SaleItem line = pendingLine();
        Sale sale = saleWithLine(line);

        saleService.closeUnidentified(sale.getId(), line.getId(), "admin");

        verify(saleItemRepository).assign(eq(line.getId()), eq(unidentified), eq("SIN-IDENT"), eq("Granola de coco"),
            eq(new BigDecimal("0.00")), eq(SaleItemAssignmentStatus.SIN_IDENTIFICAR), eq("admin"), any());
    }

    private SaleItem pendingLine() {
        return SaleItem.builder().id(UUID.randomUUID()).product(unidentified).productName("Granola de coco")
            .productCode("SIN-IDENT").freeName("Granola de coco").freeLineType(ProductSpecialType.UNIDENTIFIED)
            .assignmentStatus(SaleItemAssignmentStatus.POR_ASIGNAR).unitPrice(new BigDecimal("25.00"))
            .finalUnitPrice(new BigDecimal("25.00")).unitCost(BigDecimal.ZERO).quantity(new BigDecimal("2.000"))
            .subtotal(new BigDecimal("50.00")).build();
    }

    private Sale saleWithLine(SaleItem line) {
        Sale sale = Sale.builder().id(UUID.randomUUID()).saleNumber("VTA-000007").cashSession(session)
            .status(SaleStatus.COMPLETADA).totalAmount(line.getSubtotal()).amountCash(line.getSubtotal())
            .amountQr(BigDecimal.ZERO).paymentMethod(PaymentMethod.EFECTIVO).items(new ArrayList<>(List.of(line))).build();
        line.setSale(sale);
        when(saleItemRepository.findByIdForUpdate(line.getId())).thenReturn(Optional.of(line));
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));
        return sale;
    }
}
