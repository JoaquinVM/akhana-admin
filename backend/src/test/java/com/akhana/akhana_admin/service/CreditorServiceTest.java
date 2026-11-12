package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.impl.CreditorServiceImpl;
import com.akhana.akhana_admin.service.impl.StoreFormat;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
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
class CreditorServiceTest {

    @Mock private CreditorRepository creditorRepository;
    @Mock private CreditorRepaymentRepository creditorRepaymentRepository;
    @Mock private CreditorRepaymentItemRepository creditorRepaymentItemRepository;
    @Mock private PurchasePaymentRepository purchasePaymentRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private FinancialAccountRepository financialAccountRepository;
    @Mock private FinancialCategoryRepository financialCategoryRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SaleItemRepository saleItemRepository;
    @Mock private PurchaseShippingChargeRepository purchaseShippingChargeRepository;

    private CreditorServiceImpl creditorService;

    private Creditor mami;
    private FinancialAccount bisa;
    private FinancialAccount caja;
    private FinancialCategory pedido;
    private CashSession openSession;
    private final List<CreditorRepayment> repayments = new ArrayList<>();
    private final List<PurchasePayment> payments = new ArrayList<>();
    private final List<PurchaseShippingCharge> shippings = new ArrayList<>();
    private final List<SaleItem> saleLines = new ArrayList<>();
    private final List<CreditorRepaymentItem> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        creditorService = new CreditorServiceImpl(creditorRepository, creditorRepaymentRepository, creditorRepaymentItemRepository,
            purchasePaymentRepository,
            purchaseShippingChargeRepository, cashSessionRepository, financialAccountRepository, financialCategoryRepository, financialTransactionRepository,
            new ExpectedCashCalculator(financialTransactionRepository), productRepository, saleItemRepository);

        mami = Creditor.builder().id(UUID.randomUUID()).name("Mami").status(CreditorStatus.ACTIVO).build();
        bisa = FinancialAccount.builder().id(UUID.randomUUID()).name("Bisa")
            .currentBalance(new BigDecimal("1000.00")).isCashAccount(false).build();
        caja = FinancialAccount.builder().id(UUID.randomUUID()).name("Caja")
            .currentBalance(new BigDecimal("500.00")).isCashAccount(true).build();
        pedido = FinancialCategory.builder().id(UUID.randomUUID()).name("Pedido").type(FinancialCategoryType.GASTO)
            .systemKey(FinancialCategorySystemKey.PURCHASE_PAYMENT).build();
        // CAJ-11: a session of 31-10 still open after midnight
        openSession = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00")).totalSalesCash(BigDecimal.ZERO)
            .operationDate(LocalDate.of(2026, 10, 31)).build();

        when(creditorRepository.findById(mami.getId())).thenReturn(Optional.of(mami));
        when(creditorRepository.findByIdForUpdate(mami.getId())).thenReturn(Optional.of(mami));
        when(creditorRepository.save(any(Creditor.class))).thenAnswer(inv -> {
            Creditor c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
        when(creditorRepository.computeDebt(mami.getId())).thenReturn(BigDecimal.ZERO);
        when(creditorRepaymentRepository.save(any(CreditorRepayment.class))).thenAnswer(inv -> {
            CreditorRepayment r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
                repayments.add(r);
            }
            return r;
        });
        when(creditorRepaymentRepository.findByCreditorId(mami.getId())).thenReturn(repayments);
        when(purchasePaymentRepository.findByCreditorId(mami.getId())).thenReturn(payments);
        when(purchaseShippingChargeRepository.findByCreditorId(mami.getId())).thenReturn(shippings);
        when(saleItemRepository.findThirdPartyLinesByCreditorId(mami.getId())).thenReturn(saleLines);
        when(creditorRepaymentItemRepository.findByCreditorId(mami.getId())).thenReturn(rows);
        when(creditorRepaymentItemRepository.save(any(CreditorRepaymentItem.class))).thenAnswer(inv -> {
            CreditorRepaymentItem row = inv.getArgument(0);
            rows.add(row);
            return row;
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
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(caja.getId())).thenReturn(true);
        when(financialAccountRepository.findActiveByIdForUpdate(bisa.getId())).thenReturn(Optional.of(bisa));
        when(financialAccountRepository.findActiveByIdForUpdate(caja.getId())).thenReturn(Optional.of(caja));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(any())).thenReturn(BigDecimal.ZERO);
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(any())).thenReturn(BigDecimal.ZERO);
    }

    private void debt(String amount) {
        when(creditorRepository.computeDebt(mami.getId())).thenReturn(new BigDecimal(amount));
    }

    private PurchasePayment payment(String amount, Instant date) {
        Supplier supplier = Supplier.builder().id(UUID.randomUUID()).name("Granel").build();
        Purchase purchase = Purchase.builder().id(UUID.randomUUID())
            .purchaseNumber(String.format("PED-%06d", payments.size() + 1)).supplier(supplier)
            .purchaseDate(LocalDate.now()).build();
        PurchasePayment p = PurchasePayment.builder().id(UUID.randomUUID()).purchase(purchase).creditor(mami)
            .amount(new BigDecimal(amount)).paymentDate(date).operationDate(StoreFormat.storeDay(date)).createdAt(date)
            .status(PurchasePaymentRecordStatus.ACTIVO).build();
        payments.add(p);
        return p;
    }

    private PurchaseShippingCharge shipping(String amount, Instant date) {
        Supplier supplier = Supplier.builder().id(UUID.randomUUID()).name("Transporte").build();
        Purchase purchase = Purchase.builder().id(UUID.randomUUID()).purchaseNumber("PED-000020").supplier(supplier)
            .purchaseDate(LocalDate.now()).build();
        PurchaseShippingCharge c = PurchaseShippingCharge.builder().id(UUID.randomUUID()).purchase(purchase).creditor(mami)
            .amount(new BigDecimal(amount)).chargeDate(date).operationDate(StoreFormat.storeDay(date)).createdAt(date)
            .status(PurchasePaymentRecordStatus.ACTIVO).build();
        shippings.add(c);
        return c;
    }

    // POS-13: a sale with one line of a product of Mami
    private Sale sale(String number, String amount, Instant date) {
        Sale s = Sale.builder().id(UUID.randomUUID()).saleNumber(number).status(SaleStatus.COMPLETADA)
            .createdAt(date).operationDate(StoreFormat.storeDay(date)).build();
        saleLines.add(SaleItem.builder().sale(s).subtotal(new BigDecimal(amount)).build());
        return s;
    }

    private FinancialCategory thirdPartyCategory() {
        FinancialCategory thirdParty = FinancialCategory.builder().id(UUID.randomUUID()).name("Pago a tercero")
            .type(FinancialCategoryType.GASTO).systemKey(FinancialCategorySystemKey.THIRD_PARTY_PAYMENT).build();
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.THIRD_PARTY_PAYMENT))
            .thenReturn(Optional.of(thirdParty));
        return thirdParty;
    }

    private static CreditorRepaymentRequest.Item item(CreditorHistoryResponse.EntryType type, UUID id) {
        return new CreditorRepaymentRequest.Item(type, id);
    }

    private static CreditorRepaymentRequest pay(FinancialAccount account, String amount, CreditorRepaymentRequest.Item... items) {
        return pay(account, amount, null, items);
    }

    private static CreditorRepaymentRequest pay(FinancialAccount account, String amount, String comment,
                                                CreditorRepaymentRequest.Item... items) {
        return new CreditorRepaymentRequest(account.getId(), new BigDecimal(amount), comment, List.of(items));
    }

    private CreditorRepayment repayment(FinancialAccount account, String amount, Instant date) {
        CreditorRepayment r = CreditorRepayment.builder().id(UUID.randomUUID()).creditor(mami).account(account)
            .amount(new BigDecimal(amount)).repaymentDate(date).operationDate(StoreFormat.storeDay(date)).createdAt(date)
            .status(CreditorRepaymentStatus.ACTIVO).build();
        repayments.add(r);
        when(creditorRepaymentRepository.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private FinancialTransaction repaymentExpense(CreditorRepayment r, UUID sessionId) {
        FinancialTransaction tx = FinancialTransaction.builder().id(UUID.randomUUID())
            .type(FinancialTransactionType.GASTO_MANUAL).sourceAccount(r.getAccount()).amount(r.getAmount())
            .status(FinancialTransactionStatus.ACTIVO).creditorRepaymentId(r.getId()).cashSessionId(sessionId).build();
        when(financialTransactionRepository.findByCreditorRepaymentId(r.getId())).thenReturn(Optional.of(tx));
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(r.getAccount()));
        return tx;
    }

    // ---------------------------------------------------------------- DEU-01

    @Test
    @DisplayName("DEU-01: el nombre es único sin distinguir mayúsculas, excluyendo eliminados")
    void createCreditor_nameIsUnique() {
        when(creditorRepository.existsByNameIgnoreCaseAndStatusNot("mami", CreditorStatus.ELIMINADO)).thenReturn(true);

        assertThatThrownBy(() -> creditorService.createCreditor(new CreditorRequest("  mami ", null, null, null), "operator"))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessage("Ya existe una persona con el nombre 'mami'.");

        CreditorResponse created = creditorService.createCreditor(
            new CreditorRequest(" Tía Rosa ", " 7000000 ", "  ", null), "operator");
        assertThat(created.name()).isEqualTo("Tía Rosa");
        assertThat(created.phone()).isEqualTo("7000000");
        assertThat(created.description()).isNull();
        assertThat(created.status()).isEqualTo(CreditorStatus.ACTIVO);
        assertThat(created.debt()).isEqualByComparingTo("0.00");
        assertThat(created.createdBy()).isEqualTo("operator");
    }

    @Test
    @DisplayName("DEU-01: editar cambia nombre, teléfono y descripción con auditoría; la deuda no se toca")
    void updateCreditor_keepsDebt() {
        debt("150.00");

        CreditorResponse res = creditorService.updateCreditor(mami.getId(),
            new CreditorRequest("Mamá", "7111111", "Paga pedidos", null), "admin");

        assertThat(res.name()).isEqualTo("Mamá");
        assertThat(res.phone()).isEqualTo("7111111");
        assertThat(res.debt()).isEqualByComparingTo("150.00");
        assertThat(res.updatedBy()).isEqualTo("admin");
        assertThat(res.status()).isEqualTo(CreditorStatus.ACTIVO);
    }

    @Test
    @DisplayName("DEU-01: no se inactiva ni elimina a una persona con deuda")
    void deactivateOrDelete_rejectedWithDebt() {
        debt("150.00");

        assertThatThrownBy(() -> creditorService.updateCreditor(mami.getId(),
            new CreditorRequest("Mami", null, null, CreditorStatus.INACTIVO), "admin"))
            .hasMessage("Mami tiene una deuda de Bs 150,00; devuélvala antes de darla de baja.");
        assertThatThrownBy(() -> creditorService.deleteCreditor(mami.getId(), "admin"))
            .hasMessage("Mami tiene una deuda de Bs 150,00; devuélvala antes de darla de baja.");
        assertThat(mami.getStatus()).isEqualTo(CreditorStatus.ACTIVO);
    }

    @Test
    @DisplayName("DEU-01: con deuda 0 se inactiva o elimina (baja lógica); una eliminada no se edita")
    void deactivateAndDelete_withoutDebt() {
        CreditorResponse res = creditorService.updateCreditor(mami.getId(),
            new CreditorRequest("Mami", null, null, CreditorStatus.INACTIVO), "admin");
        assertThat(res.status()).isEqualTo(CreditorStatus.INACTIVO);

        creditorService.deleteCreditor(mami.getId(), "admin");
        assertThat(mami.getStatus()).isEqualTo(CreditorStatus.ELIMINADO);
        assertThat(mami.getDeletedBy()).isEqualTo("admin");
        assertThat(mami.getDeletedAt()).isNotNull();
        verify(creditorRepository, never()).delete(any());

        assertThatThrownBy(() -> creditorService.deleteCreditor(mami.getId(), "admin"))
            .hasMessage("La persona ya está eliminada.");
        assertThatThrownBy(() -> creditorService.updateCreditor(mami.getId(),
            new CreditorRequest("Mami", null, null, null), "admin"))
            .hasMessage("Una persona eliminada no se puede editar.");
    }

    @Test
    @DisplayName("DEU-04: el listado trae la deuda de cada persona")
    void getCreditors_withDebts() {
        Creditor rosa = Creditor.builder().id(UUID.randomUUID()).name("Rosa").status(CreditorStatus.INACTIVO).build();
        when(creditorRepository.search(null, "a")).thenReturn(List.of(mami, rosa));
        when(creditorRepository.computeDebts()).thenReturn(List.<Object[]>of(new Object[] {mami.getId(), new BigDecimal("150")}));

        List<CreditorResponse> res = creditorService.getCreditors(null, " a ");

        assertThat(res).extracting(CreditorResponse::name).containsExactly("Mami", "Rosa");
        assertThat(res.get(0).debt()).isEqualByComparingTo("150.00");
        assertThat(res.get(1).debt()).isEqualByComparingTo("0.00");
    }

    // ---------------------------------------------------------------- POS-13

    @Test
    @DisplayName("DEU-03 (062): marcar ventas de Mami las paga como Pago a tercero y cada una queda pagada")
    void registerRepayment_thirdParty() {
        debt("60.00");
        FinancialCategory thirdParty = thirdPartyCategory();
        Sale first = sale("VTA-000260", "30.00", Instant.parse("2026-11-05T21:51:00Z"));
        Sale second = sale("VTA-000357", "30.00", Instant.parse("2026-11-09T18:31:00Z"));

        CreditorHistoryResponse history = creditorService.registerRepayment(mami.getId(),
            pay(bisa, "60", item(CreditorHistoryResponse.EntryType.SALE, first.getId()),
                item(CreditorHistoryResponse.EntryType.SALE, second.getId())), "operator");

        assertThat(repayments).hasSize(1);
        assertThat(repayments.getFirst().getConcept()).isEqualTo(CreditorRepaymentConcept.THIRD_PARTY);
        assertThat(repayments.getFirst().getAmount()).isEqualByComparingTo("60.00");
        assertThat(rows).extracting(CreditorRepaymentItem::getSale).containsExactly(first, second);
        assertThat(rows).extracting(CreditorRepaymentItem::getAmount)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(new BigDecimal("30.00"), new BigDecimal("30.00"));
        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isSameAs(thirdParty);
        assertThat(captor.getValue().getDescription()).isEqualTo("Pago a tercero a Mami");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("60.00");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("940.00");

        // Both sales show as paid that day, and the repayment says what it paid
        assertThat(history.entries()).filteredOn(e -> e.type() == CreditorHistoryResponse.EntryType.SALE)
            .allSatisfy(e -> {
                assertThat(e.pendingAmount()).isEqualByComparingTo("0.00");
                assertThat(e.paidOn()).isEqualTo(repayments.getFirst().getOperationDate());
            });
        assertThat(history.entries().getLast().paidItems()).containsExactly("VTA-000260", "VTA-000357");
        assertThat(history.creditor().debt()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("DEU-03 (062): marcar ventas, un pedido y un envío registra dos: Pago a tercero por las ventas y Pedido por el resto")
    void registerRepayment_mixedConcepts() {
        debt("180.00");
        FinancialCategory thirdParty = thirdPartyCategory();
        Sale sale = sale("VTA-000506", "27.00", Instant.parse("2026-11-12T16:27:00Z"));
        PurchasePayment p = payment("122.00", Instant.parse("2026-11-03T02:08:00Z"));
        PurchaseShippingCharge shipping = shipping("31.00", Instant.parse("2026-11-04T12:00:00Z"));

        creditorService.registerRepayment(mami.getId(), pay(bisa, "180",
            item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId()),
            item(CreditorHistoryResponse.EntryType.SALE, sale.getId()),
            item(CreditorHistoryResponse.EntryType.SHIPPING, shipping.getId())), "operator");

        assertThat(repayments).extracting(CreditorRepayment::getConcept)
            .containsExactly(CreditorRepaymentConcept.THIRD_PARTY, CreditorRepaymentConcept.PURCHASE);
        assertThat(repayments).extracting(CreditorRepayment::getAmount)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(new BigDecimal("27.00"), new BigDecimal("153.00"));
        CreditorRepayment purchaseRepayment = repayments.get(1);
        assertThat(rows).filteredOn(r -> r.getRepayment() == purchaseRepayment)
            .extracting(CreditorRepaymentItem::debtId).containsExactly(p.getId(), shipping.getId());
        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(FinancialTransaction::getCategory).containsExactly(thirdParty, pedido);
        assertThat(captor.getAllValues()).extracting(FinancialTransaction::getCreditorRepaymentId)
            .containsExactly(repayments.get(0).getId(), repayments.get(1).getId());
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("820.00");
    }

    @Test
    @DisplayName("POS-13: el historial muestra cada venta con productos de Mami y la anulada no cuenta")
    void history_includesThirdPartySales() {
        Sale sale = Sale.builder().id(UUID.randomUUID()).saleNumber("VTA-000123").status(SaleStatus.COMPLETADA)
            .createdAt(Instant.parse("2026-10-20T15:00:00Z")).operationDate(LocalDate.of(2026, 10, 20)).build();
        Sale voided = Sale.builder().id(UUID.randomUUID()).saleNumber("VTA-000124").status(SaleStatus.ANULADA)
            .createdAt(Instant.parse("2026-10-21T15:00:00Z")).operationDate(LocalDate.of(2026, 10, 21)).build();
        when(saleItemRepository.findThirdPartyLinesByCreditorId(mami.getId())).thenReturn(List.of(
            SaleItem.builder().sale(sale).subtotal(new BigDecimal("40.00")).build(),
            SaleItem.builder().sale(sale).subtotal(new BigDecimal("20.00")).build(),
            SaleItem.builder().sale(voided).subtotal(new BigDecimal("15.00")).build()));

        CreditorHistoryResponse history = creditorService.getHistory(mami.getId());

        assertThat(history.entries()).hasSize(2);
        CreditorHistoryResponse.Entry first = history.entries().getFirst();
        assertThat(first.type()).isEqualTo(CreditorHistoryResponse.EntryType.SALE);
        assertThat(first.saleNumber()).isEqualTo("VTA-000123");
        assertThat(first.amount()).isEqualByComparingTo("60.00");
        assertThat(first.balanceAfter()).isEqualByComparingTo("60.00");
        assertThat(history.entries().get(1).voided()).isTrue();
        assertThat(history.entries().get(1).balanceAfter()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("CAJ-11: el historial ordena y fecha por día de operación; una venta de Mami cargada pasada la medianoche va en el día de su caja")
    void history_byDayOfOperation() {
        // Sale of the session of 31-10 recorded at 00:20 of 01-11; a payment of Mami recorded at 23:00 of 31-10
        Sale late = Sale.builder().id(UUID.randomUUID()).saleNumber("VTA-000200").status(SaleStatus.COMPLETADA)
            .createdAt(Instant.parse("2026-11-01T04:20:00Z")).operationDate(LocalDate.of(2026, 10, 31)).build();
        when(saleItemRepository.findThirdPartyLinesByCreditorId(mami.getId())).thenReturn(List.of(
            SaleItem.builder().sale(late).subtotal(new BigDecimal("30.00")).build()));
        PurchasePayment p = payment("100.00", Instant.parse("2026-11-01T03:00:00Z"));
        repayment(bisa, "20.00", Instant.parse("2026-11-01T14:00:00Z"));

        CreditorHistoryResponse history = creditorService.getHistory(mami.getId());

        assertThat(history.entries()).extracting(CreditorHistoryResponse.Entry::type).containsExactly(
            CreditorHistoryResponse.EntryType.PAYMENT, CreditorHistoryResponse.EntryType.SALE,
            CreditorHistoryResponse.EntryType.REPAYMENT);
        assertThat(history.entries()).extracting(CreditorHistoryResponse.Entry::operationDate).containsExactly(
            LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1));
        assertThat(history.entries().get(1).date()).isEqualTo(Instant.parse("2026-11-01T04:20:00Z"));
        assertThat(p.getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        // Balances do not change
        assertThat(history.creditor().debt()).isEqualByComparingTo("110.00");
    }

    @Test
    @DisplayName("POS-13: la dueña de un producto de tercero no se da de baja")
    void delete_rejectedWhileOwningAProduct() {
        when(productRepository.findFirstByOwnerCreditorIdAndStatusNot(mami.getId(), ProductStatus.ELIMINADO))
            .thenReturn(Optional.of(Product.builder().name("Producto de Mami").build()));

        assertThatThrownBy(() -> creditorService.deleteCreditor(mami.getId(), "admin"))
            .hasMessage("Mami es dueña de 'Producto de Mami'; dé de baja el producto primero.");
    }

    // ---------------------------------------------------------------- DEU-03

    @Test
    @DisplayName("DEU-03: devolver Bs 150 desde Bisa crea la devolución y un gasto Pedido vinculado; baja Bisa")
    void registerRepayment_fromAccount() {
        debt("300.00");
        PurchasePayment p = payment("150.00", Instant.parse("2026-10-30T12:00:00Z"));
        payment("150.00", Instant.parse("2026-10-31T12:00:00Z"));

        creditorService.registerRepayment(mami.getId(),
            pay(bisa, "150", "  En efectivo a mano  ", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator");

        assertThat(repayments).hasSize(1);
        CreditorRepayment r = repayments.get(0);
        assertThat(r.getAmount()).isEqualByComparingTo("150.00");
        assertThat(r.getAccount()).isSameAs(bisa);
        assertThat(r.getComment()).isEqualTo("En efectivo a mano");
        assertThat(r.getCreatedBy()).isEqualTo("operator");
        assertThat(r.getConcept()).isEqualTo(CreditorRepaymentConcept.PURCHASE);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getPurchasePayment()).isSameAs(p);
            assertThat(row.getRepayment()).isSameAs(r);
            assertThat(row.getAmount()).isEqualByComparingTo("150.00");
        });

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        FinancialTransaction tx = captor.getValue();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.GASTO_MANUAL);
        assertThat(tx.getCategory()).isSameAs(pedido);
        assertThat(tx.getDescription()).isEqualTo("Devolución a Mami");
        assertThat(tx.getCreditorRepaymentId()).isEqualTo(r.getId());
        assertThat(tx.getPurchaseId()).isNull();
        assertThat(tx.getCashSessionId()).isNull();
        // CAJ-11: from Bisa, the repayment and its expense count for the day they are recorded
        assertThat(r.getOperationDate()).isEqualTo(StoreFormat.storeDay(r.getRepaymentDate()));
        assertThat(tx.getOperationDate()).isEqualTo(r.getOperationDate());
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("850.00");
        verifyNoInteractions(cashSessionRepository);
    }

    @Test
    @DisplayName("DEU-03: desde Caja exige caja abierta, se vincula a ella y baja el efectivo esperado")
    void registerRepayment_fromCaja() {
        debt("300.00");
        PurchasePayment p = payment("50.00", Instant.parse("2026-10-30T12:00:00Z"));
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(caja, "50", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator"))
            .hasMessage("Los movimientos de la cuenta Caja requieren una caja abierta.");
        assertThat(repayments).isEmpty();

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(openSession.getId())).thenReturn(new BigDecimal("50.00"));
        creditorService.registerRepayment(mami.getId(),
            pay(caja, "50", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCashSessionId()).isEqualTo(openSession.getId());
        // CAJ-11: from Caja, the repayment and its expense count for the day of the session
        assertThat(captor.getValue().getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(repayments.get(0).getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("450.00");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("50.00");

        // T-14, as in sales: cash session, then person, then account
        var order = inOrder(creditorRepository, cashSessionRepository, financialAccountRepository);
        order.verify(cashSessionRepository).findByStatusForUpdate(CashSessionStatus.ABIERTA);
        order.verify(creditorRepository).findByIdForUpdate(mami.getId());
        order.verify(financialAccountRepository).findActiveByIdForUpdate(caja.getId());
    }

    @Test
    @DisplayName("DEU-03 (062): sin deudas marcadas, con lo marcado ya pagado, anulado, ajeno o con otro total, no se registra nada")
    void registerRepayment_staleOrEmptySelection() {
        debt("150.00");
        PurchasePayment p = payment("100.00", Instant.parse("2026-10-30T12:00:00Z"));
        PurchasePayment voided = payment("50.00", Instant.parse("2026-10-30T13:00:00Z"));
        voided.setStatus(PurchasePaymentRecordStatus.ANULADO);
        Sale sale = sale("VTA-000519", "15.00", Instant.parse("2026-11-12T20:15:00Z"));
        String changed = "Las deudas elegidas cambiaron; vuelva a abrir el detalle.";

        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(), pay(bisa, "100"), "operator"))
            .hasMessage("Elija las deudas que se pagan.");
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(bisa.getId(), new BigDecimal("100"), null, null), "operator"))
            .hasMessage("Elija las deudas que se pagan.");
        // The screen showed another total
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(bisa, "99.99", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator"))
            .hasMessage(changed);
        // Voided, unknown, of another kind than its id
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(bisa, "50", item(CreditorHistoryResponse.EntryType.PAYMENT, voided.getId())), "operator"))
            .hasMessage(changed);
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(bisa, "100", item(CreditorHistoryResponse.EntryType.PAYMENT, UUID.randomUUID())), "operator"))
            .hasMessage(changed);
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(bisa, "15", item(CreditorHistoryResponse.EntryType.PAYMENT, sale.getId())), "operator"))
            .hasMessage(changed);
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(bisa, "0.004", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator"))
            .hasMessage("El monto debe ser mayor a Bs 0,00.");
        verify(financialTransactionRepository, never()).save(any());
        assertThat(repayments).isEmpty();
        assertThat(rows).isEmpty();

        // Paid once; ticking it again (another tab) is stale
        creditorService.registerRepayment(mami.getId(),
            pay(bisa, "100", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator");
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(bisa, "115", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId()),
                item(CreditorHistoryResponse.EntryType.SALE, sale.getId())), "operator"))
            .hasMessage(changed);
        assertThat(repayments).hasSize(1);
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("900.00");
    }

    @Test
    @DisplayName("DEU-03: la devolución no supera la deuda; a una persona inactiva sí se le devuelve")
    void registerRepayment_amountRules() {
        debt("50.00");
        PurchasePayment p = payment("60.00", Instant.parse("2026-10-30T12:00:00Z"));

        // Data where the computed debt is below the debt ticked (e.g. a repayment of before 062 not fully marked)
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            pay(bisa, "60", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator"))
            .hasMessage("La devolución supera la deuda con Mami (Bs 50,00).");
        verify(financialTransactionRepository, never()).save(any());

        debt("60.00");
        mami.setStatus(CreditorStatus.INACTIVO);
        creditorService.registerRepayment(mami.getId(),
            pay(bisa, "60", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator");
        assertThat(repayments).hasSize(1);
    }

    @Test
    @DisplayName("DEU-03: anular una devolución anula su gasto con el motivo y devuelve el monto a la cuenta")
    void voidRepayment_fromAccount() {
        CreditorRepayment r = repayment(bisa, "150.00", Instant.now());
        FinancialTransaction tx = repaymentExpense(r, null);

        creditorService.voidRepayment(mami.getId(), r.getId(), new VoidDebtRequest("  Monto errado "), "admin");

        assertThat(r.getStatus()).isEqualTo(CreditorRepaymentStatus.ANULADO);
        assertThat(r.getVoidReason()).isEqualTo("Monto errado");
        assertThat(r.getVoidedBy()).isEqualTo("admin");
        assertThat(tx.getStatus()).isEqualTo(FinancialTransactionStatus.ANULADO);
        assertThat(tx.getVoidReason()).isEqualTo("Monto errado");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1150.00");

        assertThatThrownBy(() -> creditorService.voidRepayment(mami.getId(), r.getId(), new VoidDebtRequest("Otra"), "admin"))
            .hasMessage("La devolución ya está anulada.");
        assertThatThrownBy(() -> creditorService.voidRepayment(mami.getId(), r.getId(), new VoidDebtRequest("  "), "admin"))
            .hasMessage("El motivo de anulación es obligatorio.");
    }

    @Test
    @DisplayName("DEU-03: anular una devolución de Caja sube el efectivo esperado; con su caja cerrada se rechaza")
    void voidRepayment_fromCaja() {
        CreditorRepayment r = repayment(caja, "50.00", Instant.now());
        FinancialTransaction tx = repaymentExpense(r, openSession.getId());

        CashSession closed = CashSession.builder().id(openSession.getId()).status(CashSessionStatus.CERRADA).build();
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> creditorService.voidRepayment(mami.getId(), r.getId(), new VoidDebtRequest("Error"), "admin"))
            .hasMessage("La devolución se hizo en una caja cerrada; no se puede anular.");
        assertThat(tx.getStatus()).isEqualTo(FinancialTransactionStatus.ACTIVO);
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("500.00");

        openSession.setExpectedCash(new BigDecimal("50.00"));
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(openSession));
        creditorService.voidRepayment(mami.getId(), r.getId(), new VoidDebtRequest("Error"), "admin");

        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("550.00");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("100.00");
        var order = inOrder(creditorRepository, cashSessionRepository, financialAccountRepository);
        // The closed attempt stops at the session, before locking the person
        order.verify(cashSessionRepository, times(2)).findByIdForUpdate(openSession.getId());
        order.verify(creditorRepository).findByIdForUpdate(mami.getId());
        order.verify(financialAccountRepository).findAllByIdForUpdate(any());
    }

    // ---------------------------------------------------------------- DEU-04

    @Test
    @DisplayName("DEU-04: Mami pagó 200 y 100 y se le devolvieron 150: deuda 150, saldo tras cada registro; anulados no cuentan")
    void getHistory_balanceAfterEachEntry() {
        Instant t0 = Instant.parse("2026-10-01T12:00:00Z");
        payment("200.00", t0);
        repayment(bisa, "150.00", t0.plusSeconds(3600));
        payment("100.00", t0.plusSeconds(60));
        CreditorRepayment voided = repayment(caja, "40.00", t0.plusSeconds(7200));
        voided.setStatus(CreditorRepaymentStatus.ANULADO);
        voided.setVoidReason("Duplicada");

        CreditorHistoryResponse res = creditorService.getHistory(mami.getId());

        assertThat(res.creditor().debt()).isEqualByComparingTo("150.00");
        assertThat(res.entries()).extracting(CreditorHistoryResponse.Entry::type).containsExactly(
            CreditorHistoryResponse.EntryType.PAYMENT, CreditorHistoryResponse.EntryType.PAYMENT,
            CreditorHistoryResponse.EntryType.REPAYMENT, CreditorHistoryResponse.EntryType.REPAYMENT);
        assertThat(res.entries()).extracting(CreditorHistoryResponse.Entry::balanceAfter)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("150"), new BigDecimal("150"));
        CreditorHistoryResponse.Entry first = res.entries().get(0);
        assertThat(first.purchaseNumber()).isEqualTo("PED-000001");
        assertThat(first.supplierName()).isEqualTo("Granel");
        CreditorHistoryResponse.Entry last = res.entries().get(3);
        assertThat(last.voided()).isTrue();
        assertThat(last.voidReason()).isEqualTo("Duplicada");
        assertThat(last.accountName()).isEqualTo("Caja");
    }

    @Test
    @DisplayName("DEU-04 (062): cada deuda dice si está pagada o cuánto falta; al anular la devolución vuelve a pendiente")
    void getHistory_pendingAndPaid() {
        Instant t0 = Instant.parse("2026-11-02T12:00:00Z");
        PurchasePayment first = payment("200.00", t0);
        PurchasePayment second = payment("100.00", t0.plusSeconds(60));
        PurchaseShippingCharge shipping = shipping("20.00", t0.plusSeconds(120));
        CreditorRepayment r = repayment(bisa, "250.00", t0.plusSeconds(86400));
        // A repayment of before 062 that covered the second payment only in part
        rows.add(CreditorRepaymentItem.builder().repayment(r).purchasePayment(second).amount(new BigDecimal("50.00")).build());
        rows.add(CreditorRepaymentItem.builder().repayment(r).purchasePayment(first).amount(new BigDecimal("200.00")).build());

        CreditorHistoryResponse res = creditorService.getHistory(mami.getId());

        assertThat(res.creditor().debt()).isEqualByComparingTo("70.00");
        CreditorHistoryResponse.Entry paid = res.entries().get(0);
        assertThat(paid.pendingAmount()).isEqualByComparingTo("0.00");
        assertThat(paid.paidOn()).isEqualTo(r.getOperationDate());
        CreditorHistoryResponse.Entry half = res.entries().get(1);
        assertThat(half.pendingAmount()).isEqualByComparingTo("50.00");
        assertThat(half.paidOn()).isNull();
        CreditorHistoryResponse.Entry shippingEntry = res.entries().get(2);
        assertThat(shippingEntry.type()).isEqualTo(CreditorHistoryResponse.EntryType.SHIPPING);
        assertThat(shippingEntry.pendingAmount()).isEqualByComparingTo("20.00");
        CreditorHistoryResponse.Entry repaymentEntry = res.entries().get(3);
        assertThat(repaymentEntry.pendingAmount()).isNull();
        // Oldest debt first, whatever the order of the rows
        assertThat(repaymentEntry.paidItems()).containsExactly("PED-000001", "PED-000002");

        r.setStatus(CreditorRepaymentStatus.ANULADO);
        CreditorHistoryResponse voided = creditorService.getHistory(mami.getId());

        assertThat(voided.creditor().debt()).isEqualByComparingTo("320.00");
        assertThat(voided.entries()).extracting(CreditorHistoryResponse.Entry::pendingAmount)
            .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .containsExactly(new BigDecimal("200.00"), new BigDecimal("100.00"), new BigDecimal("20.00"), null);
        assertThat(voided.entries().get(0).paidOn()).isNull();
        // The voided repayment still says what it had paid
        assertThat(voided.entries().get(3).paidItems()).containsExactly("PED-000001", "PED-000002");
    }

    @Test
    @DisplayName("DEU-03 (062): anular la devolución deja otra vez pendientes sus deudas, que se pueden volver a marcar")
    void voidRepayment_debtsPendingAgain() {
        debt("100.00");
        PurchasePayment p = payment("100.00", Instant.parse("2026-10-30T12:00:00Z"));
        creditorService.registerRepayment(mami.getId(),
            pay(bisa, "100", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator");
        CreditorRepayment r = repayments.getFirst();
        when(creditorRepaymentRepository.findById(r.getId())).thenReturn(Optional.of(r));
        FinancialTransaction tx = FinancialTransaction.builder().id(UUID.randomUUID()).sourceAccount(bisa)
            .amount(r.getAmount()).status(FinancialTransactionStatus.ACTIVO).creditorRepaymentId(r.getId()).build();
        when(financialTransactionRepository.findByCreditorRepaymentId(r.getId())).thenReturn(Optional.of(tx));
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisa));

        CreditorHistoryResponse res = creditorService.voidRepayment(mami.getId(), r.getId(), new VoidDebtRequest("Error"), "admin");

        assertThat(res.entries().getFirst().pendingAmount()).isEqualByComparingTo("100.00");
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("1000.00");
        creditorService.registerRepayment(mami.getId(),
            pay(bisa, "100", item(CreditorHistoryResponse.EntryType.PAYMENT, p.getId())), "operator");
        assertThat(repayments).hasSize(2);
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("900.00");
    }
}
