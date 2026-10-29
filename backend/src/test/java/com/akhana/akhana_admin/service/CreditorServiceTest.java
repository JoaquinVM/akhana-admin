package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.impl.CreditorServiceImpl;
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
    @Mock private PurchasePaymentRepository purchasePaymentRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private FinancialAccountRepository financialAccountRepository;
    @Mock private FinancialCategoryRepository financialCategoryRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SaleItemRepository saleItemRepository;

    private CreditorServiceImpl creditorService;

    private Creditor mami;
    private FinancialAccount bisa;
    private FinancialAccount caja;
    private FinancialCategory pedido;
    private CashSession openSession;
    private final List<CreditorRepayment> repayments = new ArrayList<>();
    private final List<PurchasePayment> payments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        creditorService = new CreditorServiceImpl(creditorRepository, creditorRepaymentRepository, purchasePaymentRepository,
            cashSessionRepository, financialAccountRepository, financialCategoryRepository, financialTransactionRepository,
            new ExpectedCashCalculator(financialTransactionRepository), productRepository, saleItemRepository);

        mami = Creditor.builder().id(UUID.randomUUID()).name("Mami").status(CreditorStatus.ACTIVO).build();
        bisa = FinancialAccount.builder().id(UUID.randomUUID()).name("Bisa")
            .currentBalance(new BigDecimal("1000.00")).isCashAccount(false).build();
        caja = FinancialAccount.builder().id(UUID.randomUUID()).name("Caja")
            .currentBalance(new BigDecimal("500.00")).isCashAccount(true).build();
        pedido = FinancialCategory.builder().id(UUID.randomUUID()).name("Pedido").type(FinancialCategoryType.GASTO)
            .systemKey(FinancialCategorySystemKey.PURCHASE_PAYMENT).build();
        openSession = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00")).totalSalesCash(BigDecimal.ZERO).build();

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
        Purchase purchase = Purchase.builder().id(UUID.randomUUID()).purchaseNumber("PED-000001").supplier(supplier)
            .purchaseDate(LocalDate.now()).build();
        PurchasePayment p = PurchasePayment.builder().id(UUID.randomUUID()).purchase(purchase).creditor(mami)
            .amount(new BigDecimal(amount)).paymentDate(date).createdAt(date).status(PurchasePaymentRecordStatus.ACTIVO).build();
        payments.add(p);
        return p;
    }

    private CreditorRepayment repayment(FinancialAccount account, String amount, Instant date) {
        CreditorRepayment r = CreditorRepayment.builder().id(UUID.randomUUID()).creditor(mami).account(account)
            .amount(new BigDecimal(amount)).repaymentDate(date).createdAt(date).status(CreditorRepaymentStatus.ACTIVO).build();
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
    @DisplayName("POS-13: el pago a tercero usa la categoría Pago a tercero y queda con su concepto")
    void registerRepayment_thirdParty() {
        debt("60.00");
        FinancialCategory thirdParty = FinancialCategory.builder().id(UUID.randomUUID()).name("Pago a tercero")
            .type(FinancialCategoryType.GASTO).systemKey(FinancialCategorySystemKey.THIRD_PARTY_PAYMENT).build();
        when(financialCategoryRepository.findBySystemKeyAndDeletedAtIsNull(FinancialCategorySystemKey.THIRD_PARTY_PAYMENT))
            .thenReturn(Optional.of(thirdParty));
        when(creditorRepository.computeThirdPartyPending(mami.getId())).thenReturn(new BigDecimal("60.00"));

        creditorService.registerRepayment(mami.getId(), new CreditorRepaymentRequest(bisa.getId(), new BigDecimal("60"),
            null, CreditorRepaymentConcept.THIRD_PARTY), "operator");

        assertThat(repayments.getFirst().getConcept()).isEqualTo(CreditorRepaymentConcept.THIRD_PARTY);
        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isSameAs(thirdParty);
        assertThat(captor.getValue().getDescription()).isEqualTo("Pago a tercero a Mami");
    }

    @Test
    @DisplayName("P-48: el pago a tercero no supera lo pendiente de las ventas de la persona")
    void registerRepayment_thirdPartyOverPending() {
        debt("100.00");
        when(creditorRepository.computeThirdPartyPending(mami.getId())).thenReturn(new BigDecimal("30.00"));

        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(), new CreditorRepaymentRequest(bisa.getId(),
            new BigDecimal("30.01"), null, CreditorRepaymentConcept.THIRD_PARTY), "operator"))
            .hasMessage("El Pago a tercero supera lo pendiente de las ventas de Mami (Bs 30,00); registre el resto con el concepto Pedido.");
        assertThat(repayments).isEmpty();
    }

    @Test
    @DisplayName("POS-13: el historial muestra cada venta con productos de Mami y la anulada no cuenta")
    void history_includesThirdPartySales() {
        Sale sale = Sale.builder().id(UUID.randomUUID()).saleNumber("VTA-000123").status(SaleStatus.COMPLETADA)
            .createdAt(Instant.parse("2026-10-20T15:00:00Z")).build();
        Sale voided = Sale.builder().id(UUID.randomUUID()).saleNumber("VTA-000124").status(SaleStatus.ANULADA)
            .createdAt(Instant.parse("2026-10-21T15:00:00Z")).build();
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

        creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(bisa.getId(), new BigDecimal("150"), "  En efectivo a mano  "), "operator");

        assertThat(repayments).hasSize(1);
        CreditorRepayment r = repayments.get(0);
        assertThat(r.getAmount()).isEqualByComparingTo("150.00");
        assertThat(r.getAccount()).isSameAs(bisa);
        assertThat(r.getComment()).isEqualTo("En efectivo a mano");
        assertThat(r.getCreatedBy()).isEqualTo("operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        FinancialTransaction tx = captor.getValue();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.GASTO_MANUAL);
        assertThat(tx.getCategory()).isSameAs(pedido);
        assertThat(tx.getDescription()).isEqualTo("Devolución a Mami");
        assertThat(tx.getCreditorRepaymentId()).isEqualTo(r.getId());
        assertThat(tx.getPurchaseId()).isNull();
        assertThat(tx.getCashSessionId()).isNull();
        assertThat(bisa.getCurrentBalance()).isEqualByComparingTo("850.00");
        verifyNoInteractions(cashSessionRepository);
    }

    @Test
    @DisplayName("DEU-03: desde Caja exige caja abierta, se vincula a ella y baja el efectivo esperado")
    void registerRepayment_fromCaja() {
        debt("300.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(caja.getId(), new BigDecimal("50"), null), "operator"))
            .hasMessage("Los movimientos de la cuenta Caja requieren una caja abierta.");
        assertThat(repayments).isEmpty();

        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(openSession.getId())).thenReturn(new BigDecimal("50.00"));
        creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(caja.getId(), new BigDecimal("50"), null), "operator");

        ArgumentCaptor<FinancialTransaction> captor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getCashSessionId()).isEqualTo(openSession.getId());
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("450.00");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("50.00");

        // T-14, as in sales: cash session, then person, then account
        var order = inOrder(creditorRepository, cashSessionRepository, financialAccountRepository);
        order.verify(cashSessionRepository).findByStatusForUpdate(CashSessionStatus.ABIERTA);
        order.verify(creditorRepository).findByIdForUpdate(mami.getId());
        order.verify(financialAccountRepository).findActiveByIdForUpdate(caja.getId());
    }

    @Test
    @DisplayName("DEU-03: la devolución no supera la deuda ni es 0; a una persona inactiva sí se le devuelve")
    void registerRepayment_amountRules() {
        debt("50.00");

        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(bisa.getId(), new BigDecimal("50.01"), null), "operator"))
            .hasMessage("La devolución supera la deuda con Mami (Bs 50,00).");
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(bisa.getId(), new BigDecimal("0.004"), null), "operator"))
            .hasMessage("El monto debe ser mayor a Bs 0,00.");
        debt("0");
        assertThatThrownBy(() -> creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(bisa.getId(), new BigDecimal("1"), null), "operator"))
            .hasMessage("La devolución supera la deuda con Mami (Bs 0,00).");
        verify(financialTransactionRepository, never()).save(any());

        debt("50.00");
        mami.setStatus(CreditorStatus.INACTIVO);
        creditorService.registerRepayment(mami.getId(),
            new CreditorRepaymentRequest(bisa.getId(), new BigDecimal("50"), null), "operator");
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
}
