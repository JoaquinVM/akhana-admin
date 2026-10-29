package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.CashTransferRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.dto.PaymentAccountResponse;
import com.akhana.akhana_admin.dto.VoidFinancialTransactionRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import com.akhana.akhana_admin.service.impl.FinancialTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialCategoryRepository financialCategoryRepository;

    @Mock
    private CashSessionRepository cashSessionRepository;

    private FinancialTransactionServiceImpl financialTransactionService;

    private FinancialAccount bisaAccount;
    private FinancialAccount cajaAccount;
    private FinancialAccount joacoAccount;
    private FinancialCategory incomeCat;
    private FinancialCategory expenseCat;

    @BeforeEach
    void setUp() {
        financialTransactionService = new FinancialTransactionServiceImpl(financialTransactionRepository,
                financialAccountRepository, financialCategoryRepository, cashSessionRepository,
                new ExpectedCashCalculator(financialTransactionRepository));

        bisaAccount = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Cuenta bancaria BISA")
                .color("#1B3B18")
                .initialBalance(new BigDecimal("1000.00"))
                .currentBalance(new BigDecimal("1000.00"))
                .isCashAccount(false)
                .isQrAccount(true)
                .build();

        cajaAccount = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Caja")
                .color("#2E5B27")
                .initialBalance(BigDecimal.ZERO)
                .currentBalance(new BigDecimal("500.00"))
                .isCashAccount(true)
                .isQrAccount(false)
                .build();

        joacoAccount = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Cuenta Joaco")
                .color("#F5B800")
                .initialBalance(BigDecimal.ZERO)
                .currentBalance(new BigDecimal("200.00"))
                .isCashAccount(false)
                .isQrAccount(false)
                .build();

        incomeCat = FinancialCategory.builder()
                .id(UUID.randomUUID())
                .name("Aportes")
                .type(FinancialCategoryType.INGRESO)
                .build();

        expenseCat = FinancialCategory.builder()
                .id(UUID.randomUUID())
                .name("Servicios")
                .type(FinancialCategoryType.GASTO)
                .build();
    }

    @Test
    @DisplayName("Debe registrar ingreso manual en cuenta bancaria e incrementar su saldo")
    void testCreateIncomeManual() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.INGRESO_MANUAL)
                .sourceAccountId(bisaAccount.getId())
                .categoryId(incomeCat.getId())
                .amount(new BigDecimal("250.00"))
                .description("Inyección de capital")
                .build();

        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(incomeCat.getId())).thenReturn(Optional.of(incomeCat));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");

        assertNotNull(res);
        assertEquals(new BigDecimal("1250.00"), bisaAccount.getCurrentBalance());
        verify(financialAccountRepository).save(bisaAccount);
    }

    @Test
    @DisplayName("Debe bloquear ingreso manual en cuenta Caja")
    void testCreateIncomeManualInCajaForbidden() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.INGRESO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .categoryId(incomeCat.getId())
                .amount(new BigDecimal("100.00"))
                .description("Ingreso en caja")
                .build();

        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(cajaAccount));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.createTransaction(req, "admin"));
        assertTrue(ex.getMessage().contains("cuenta Caja"));
    }

    private CashSession openSession() {
        CashSession session = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
                .openingAmount(new BigDecimal("200.00")).totalSalesCash(new BigDecimal("50.00")).build();
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(session));
        return session;
    }

    private void stubSessionSums(UUID sessionId, String outflows, String inflows) {
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(sessionId)).thenReturn(new BigDecimal(outflows));
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(sessionId)).thenReturn(new BigDecimal(inflows));
    }

    @Test
    @DisplayName("Gasto en Caja: se vincula a la caja abierta (ignora el cashSessionId del cliente) y recalcula el esperado")
    void testCreateExpenseInCaja() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("50.00"))
                .description("Compra de café y azúcar")
                .cashSessionId(UUID.randomUUID())
                .build();

        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        CashSession session = openSession();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(cajaAccount));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(expenseCat.getId())).thenReturn(Optional.of(expenseCat));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));
        stubSessionSums(session.getId(), "50.00", "0");

        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");

        assertEquals(session.getId(), res.getCashSessionId());
        assertEquals(new BigDecimal("450.00"), cajaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("200.00"), session.getExpectedCash());
        verify(financialAccountRepository).save(cajaAccount);
        verify(cashSessionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Gasto en Caja sin caja abierta: se rechaza sin tocar saldos")
    void testCreateExpenseInCajaWithoutOpenSession() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("50.00"))
                .description("Gasto fuera de caja")
                .build();

        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.createTransaction(req, "admin"));
        assertTrue(ex.getMessage().contains("requieren una caja abierta"));
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Transferencia hacia Caja: se vincula a la caja abierta y suma al esperado")
    void testTransferIntoCajaBindsOpenSession() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccountId(joacoAccount.getId())
                .destinationAccountId(cajaAccount.getId())
                .amount(new BigDecimal("80.00"))
                .description("Ingreso de cambio")
                .build();

        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(joacoAccount.getId())).thenReturn(false);
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        CashSession session = openSession();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(joacoAccount, cajaAccount));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));
        stubSessionSums(session.getId(), "0", "80.00");

        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");

        assertEquals(session.getId(), res.getCashSessionId());
        assertEquals(new BigDecimal("580.00"), cajaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("330.00"), session.getExpectedCash());
    }

    @Test
    @DisplayName("Transferencia desde Caja: se vincula a la caja abierta y resta del esperado")
    void testTransferFromCajaBindsOpenSession() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccountId(cajaAccount.getId())
                .destinationAccountId(joacoAccount.getId())
                .amount(new BigDecimal("100.00"))
                .description("Retiro de caja")
                .build();

        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        CashSession session = openSession();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(joacoAccount, cajaAccount));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));
        stubSessionSums(session.getId(), "100.00", "0");

        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");

        assertEquals(session.getId(), res.getCashSessionId());
        assertEquals(new BigDecimal("150.00"), session.getExpectedCash());
    }

    @Test
    @DisplayName("Transferencia hacia Caja sin caja abierta: se rechaza")
    void testTransferIntoCajaWithoutOpenSession() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccountId(joacoAccount.getId())
                .destinationAccountId(cajaAccount.getId())
                .amount(new BigDecimal("80.00"))
                .description("Ingreso de cambio")
                .build();

        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(joacoAccount.getId())).thenReturn(false);
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> financialTransactionService.createTransaction(req, "admin"));
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Movimiento fuera de Caja: ignora el cashSessionId del cliente y no toca la caja")
    void testNonCashMovementIgnoresClientSession() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(bisaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("30.00"))
                .description("Comisión bancaria")
                .cashSessionId(UUID.randomUUID())
                .build();

        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(expenseCat.getId())).thenReturn(Optional.of(expenseCat));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");

        assertNull(res.getCashSessionId());
        verifyNoInteractions(cashSessionRepository);
    }

    @Test
    @DisplayName("Editar un movimiento de una caja cerrada: se rechaza sin tocar saldos")
    void testUpdateMovementOfClosedSessionRejected() {
        UUID sessionId = UUID.randomUUID();
        FinancialTransaction tx = cashExpense(sessionId);
        tx.setSourceAccount(cajaAccount);
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(cashSessionRepository.findByIdForUpdate(sessionId))
                .thenReturn(Optional.of(CashSession.builder().id(sessionId).status(CashSessionStatus.CERRADA).build()));

        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("100.00"))
                .description("Corrección")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.updateTransaction(tx.getId(), req, "admin"));
        assertTrue(ex.getMessage().contains("caja cerrada"));
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("T-05: editar descripción y categoría de un gasto de la caja abierta no toca saldos ni el esperado")
    void testUpdateDescriptionAndCategoryKeepsBalances() {
        CashSession session = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
                .openingAmount(new BigDecimal("200.00")).expectedCash(new BigDecimal("150.00")).build();
        FinancialTransaction tx = cashExpense(session.getId());
        tx.setSourceAccount(cajaAccount);
        FinancialCategory otherExpenseCat = FinancialCategory.builder()
                .id(UUID.randomUUID()).name("Limpieza").type(FinancialCategoryType.GASTO).build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(cashSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(otherExpenseCat.getId())).thenReturn(Optional.of(otherExpenseCat));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .categoryId(otherExpenseCat.getId())
                .amount(new BigDecimal("100.00"))
                .description("  Productos de limpieza  ")
                .notes(" Factura 12 ")
                .build();

        FinancialTransactionResponse res = financialTransactionService.updateTransaction(tx.getId(), req, "admin");

        assertEquals("Productos de limpieza", res.getDescription());
        assertEquals("Factura 12", tx.getNotes());
        assertEquals(otherExpenseCat, tx.getCategory());
        assertEquals(session.getId(), res.getCashSessionId());
        assertEquals(new BigDecimal("100.00"), res.getAmount());
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("150.00"), session.getExpectedCash());
        verify(financialAccountRepository, never()).findAllByIdForUpdate(any());
        verify(financialAccountRepository, never()).save(any());
        verify(cashSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("T-05: editar el monto de un movimiento se rechaza; se corrige anulando y registrando de nuevo")
    void testUpdateAmountChangeRejected() {
        FinancialTransaction tx = cashExpense(null);
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(bisaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("60.00"))
                .description("Corrección")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> financialTransactionService.updateTransaction(tx.getId(), req, "admin"));
        assertTrue(ex.getMessage().contains("anúlelo y regístrelo de nuevo"));
        assertEquals(new BigDecimal("100.00"), tx.getAmount());
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
        verifyNoInteractions(financialAccountRepository, cashSessionRepository);
    }

    @Test
    @DisplayName("T-05: cambiar la cuenta de un movimiento se rechaza; se corrige anulando y registrando de nuevo")
    void testUpdateAccountChangeRejected() {
        FinancialTransaction tx = cashExpense(null);
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("100.00"))
                .description("A Caja")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> financialTransactionService.updateTransaction(tx.getId(), req, "admin"));
        assertTrue(ex.getMessage().contains("anúlelo y regístrelo de nuevo"));
        assertEquals(bisaAccount, tx.getSourceAccount());
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
        verifyNoInteractions(financialAccountRepository, cashSessionRepository);
    }

    @Test
    @DisplayName("Anular una transferencia de una caja cerrada: se rechaza sin tocar saldos")
    void testVoidTransferOfClosedSessionRejected() {
        UUID sessionId = UUID.randomUUID();
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccount(cajaAccount)
                .destinationAccount(joacoAccount)
                .amount(new BigDecimal("100.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .cashSessionId(sessionId)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(cashSessionRepository.findByIdForUpdate(sessionId))
                .thenReturn(Optional.of(CashSession.builder().id(sessionId).status(CashSessionStatus.CERRADA).build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> financialTransactionService.voidTransaction(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "admin"));
        assertTrue(ex.getMessage().contains("caja cerrada"));
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        assertEquals(FinancialTransactionStatus.ACTIVO, tx.getStatus());
    }

    @Test
    @DisplayName("Anular una transferencia hacia Caja de la caja abierta: el esperado deja de sumarla")
    void testVoidTransferIntoCajaRefreshesExpected() {
        CashSession session = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
                .openingAmount(new BigDecimal("200.00")).totalSalesCash(new BigDecimal("50.00"))
                .expectedCash(new BigDecimal("330.00")).build();
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccount(joacoAccount)
                .destinationAccount(cajaAccount)
                .amount(new BigDecimal("80.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .cashSessionId(session.getId())
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(cashSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));
        stubSessionSums(session.getId(), "0", "0");

        financialTransactionService.voidTransaction(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "admin");

        assertEquals(new BigDecimal("420.00"), cajaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("250.00"), session.getExpectedCash());
    }

    @Test
    @DisplayName("FIN-06: anular una transferencia no deja la cuenta destino en negativo")
    void testVoidTransferLeavingDestinationNegativeRejected() {
        joacoAccount.setCurrentBalance(new BigDecimal("30.00"));
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccount(bisaAccount)
                .destinationAccount(joacoAccount)
                .amount(new BigDecimal("80.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> financialTransactionService.voidTransaction(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "admin"));
        assertTrue(ex.getMessage().contains("Cuenta Joaco quedaría con saldo negativo (saldo disponible: 30.00 Bs)"));
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        assertEquals(FinancialTransactionStatus.ACTIVO, tx.getStatus());
    }

    @Test
    @DisplayName("FIN-06: anular un ingreso ya gastado no deja la cuenta en negativo")
    void testVoidIncomeLeavingAccountNegativeRejected() {
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.INGRESO_MANUAL)
                .sourceAccount(joacoAccount)
                .category(incomeCat)
                .amount(new BigDecimal("200.01"))
                .status(FinancialTransactionStatus.ACTIVO)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        assertThrows(IllegalStateException.class, () -> financialTransactionService.voidTransaction(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "admin"));
        assertEquals(new BigDecimal("200.00"), joacoAccount.getCurrentBalance());
    }

    @Test
    @DisplayName("Un gasto o ingreso manual no usa las categorías que registra el sistema (Pedido, Pago a tercero, Ajuste, ventas)")
    void testCreateWithSystemCategoryRejected() {
        FinancialCategory pedido = FinancialCategory.builder().id(UUID.randomUUID()).name("Pedido")
                .type(FinancialCategoryType.GASTO).systemKey(FinancialCategorySystemKey.PURCHASE_PAYMENT).build();
        FinancialCategory ventas = FinancialCategory.builder().id(UUID.randomUUID()).name("Ventas")
                .type(FinancialCategoryType.INGRESO).isSalesDefault(true).build();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(pedido.getId())).thenReturn(Optional.of(pedido));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(ventas.getId())).thenReturn(Optional.of(ventas));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> financialTransactionService.createTransaction(
                FinancialTransactionRequest.builder().type(FinancialTransactionType.GASTO_MANUAL).sourceAccountId(bisaAccount.getId())
                        .categoryId(pedido.getId()).amount(new BigDecimal("10")).description("Pago").build(), "admin"));
        assertTrue(ex.getMessage().startsWith("La categoría «Pedido» la registra el sistema"));
        assertThrows(IllegalArgumentException.class, () -> financialTransactionService.createTransaction(
                FinancialTransactionRequest.builder().type(FinancialTransactionType.INGRESO_MANUAL).sourceAccountId(bisaAccount.getId())
                        .categoryId(ventas.getId()).amount(new BigDecimal("10")).description("Venta").build(), "admin"));
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Transporte es categoría de sistema pero también un gasto normal")
    void testCreateWithShippingCategoryAllowed() {
        FinancialCategory transporte = FinancialCategory.builder().id(UUID.randomUUID()).name("Transporte")
                .type(FinancialCategoryType.GASTO).systemKey(FinancialCategorySystemKey.PURCHASE_SHIPPING).build();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(transporte.getId())).thenReturn(Optional.of(transporte));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        financialTransactionService.createTransaction(FinancialTransactionRequest.builder().type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(bisaAccount.getId()).categoryId(transporte.getId()).amount(new BigDecimal("10"))
                .description("Taxi").build(), "admin");

        assertEquals(new BigDecimal("990.00"), bisaAccount.getCurrentBalance());
    }

    @Test
    @DisplayName("CAJ-10: el ajuste de apertura no se anula desde el POS ni desde Finanzas")
    void testVoidCashAdjustmentRejected() {
        UUID sessionId = UUID.randomUUID();
        FinancialCategory ajuste = FinancialCategory.builder().id(UUID.randomUUID()).name("Ajuste")
                .type(FinancialCategoryType.GASTO).systemKey(FinancialCategorySystemKey.CASH_SHORTAGE).build();
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccount(cajaAccount)
                .category(ajuste)
                .amount(new BigDecimal("45.50"))
                .status(FinancialTransactionStatus.ACTIVO)
                .cashSessionId(sessionId)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        lenient().when(cashSessionRepository.findByIdForUpdate(sessionId))
                .thenReturn(Optional.of(CashSession.builder().id(sessionId).status(CashSessionStatus.ABIERTA).build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> financialTransactionService.voidCashExpense(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "operador"));
        assertTrue(ex.getMessage().contains("ajustes de caja"));
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        assertEquals(FinancialTransactionStatus.ACTIVO, tx.getStatus());
    }

    @Test
    @DisplayName("Debe procesar transferencia exitosa entre cuentas con saldo suficiente")
    void testTransferSuccess() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccountId(bisaAccount.getId())
                .destinationAccountId(joacoAccount.getId())
                .amount(new BigDecimal("300.00"))
                .description("Transferencia operativa")
                .build();

        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount, joacoAccount));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");

        assertNotNull(res);
        assertEquals(new BigDecimal("700.00"), bisaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("500.00"), joacoAccount.getCurrentBalance());
        verify(financialAccountRepository).save(bisaAccount);
        verify(financialAccountRepository).save(joacoAccount);
    }

    @Test
    @DisplayName("Debe rechazar transferencia si la cuenta origen tiene saldo insuficiente")
    void testTransferInsufficientBalance() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccountId(bisaAccount.getId())
                .destinationAccountId(joacoAccount.getId())
                .amount(new BigDecimal("1500.00"))
                .description("Transferencia excesiva")
                .build();

        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount, joacoAccount));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.createTransaction(req, "admin"));
        assertTrue(ex.getMessage().contains("Saldo insuficiente"));
    }

    @Test
    @DisplayName("Debe anular gasto manual restituyendo el saldo en la cuenta")
    void testVoidExpense() {
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccount(bisaAccount)
                .amount(new BigDecimal("100.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .build();

        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        VoidFinancialTransactionRequest voidReq = VoidFinancialTransactionRequest.builder()
                .voidReason("Error en factura")
                .build();

        FinancialTransactionResponse res = financialTransactionService.voidTransaction(tx.getId(), voidReq, "supervisor");

        assertEquals(FinancialTransactionStatus.ANULADO, res.getStatus());
        assertEquals("supervisor", res.getVoidedBy());
        assertEquals("Error en factura", res.getVoidReason());
        assertEquals(new BigDecimal("1100.00"), bisaAccount.getCurrentBalance());
        verify(financialAccountRepository).save(bisaAccount);
    }

    @Test
    @DisplayName("Debe impedir anular desde Finanzas una transacción originada por una venta del POS")
    void testVoidSaleTransactionForbidden() {
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.VENTA_INGRESO)
                .saleId(UUID.randomUUID())
                .sourceAccount(cajaAccount)
                .amount(new BigDecimal("85.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .build();

        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidTransaction(tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "admin"));
        assertTrue(ex.getMessage().contains("generadas por ventas no pueden anularse desde Finanzas"));
    }

    @Test
    @DisplayName("COM-06: los pagos y envíos de pedidos no se anulan ni editan desde Finanzas ni desde el POS")
    void testPurchaseMovementsAreManagedFromPurchases() {
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .purchaseId(UUID.randomUUID())
                .cashSessionId(UUID.randomUUID())
                .sourceAccount(cajaAccount)
                .amount(new BigDecimal("100.00"))
                .description("Pago pedido PED-000001")
                .status(FinancialTransactionStatus.ACTIVO)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        VoidFinancialTransactionRequest voidRequest = VoidFinancialTransactionRequest.builder().voidReason("Error").build();

        IllegalStateException fromFinances = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidTransaction(tx.getId(), voidRequest, "admin"));
        IllegalStateException fromPos = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidCashExpense(tx.getId(), voidRequest, "operator"));
        FinancialTransactionRequest edit = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .amount(new BigDecimal("100.00"))
                .description("Otro texto")
                .build();
        IllegalStateException onEdit = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.updateTransaction(tx.getId(), edit, "admin"));

        assertEquals("Los movimientos de un pedido se gestionan desde Compras.", fromFinances.getMessage());
        assertEquals(fromFinances.getMessage(), fromPos.getMessage());
        assertEquals(fromFinances.getMessage(), onEdit.getMessage());
        assertEquals(FinancialTransactionStatus.ACTIVO, tx.getStatus());
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEU-03: el gasto de una devolución a una persona no se anula ni edita desde Finanzas ni desde el POS")
    void testDebtMovementsAreManagedFromDebts() {
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .creditorRepaymentId(UUID.randomUUID())
                .cashSessionId(UUID.randomUUID())
                .sourceAccount(cajaAccount)
                .amount(new BigDecimal("150.00"))
                .description("Devolución a Mami")
                .status(FinancialTransactionStatus.ACTIVO)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        VoidFinancialTransactionRequest voidRequest = VoidFinancialTransactionRequest.builder().voidReason("Error").build();

        IllegalStateException fromFinances = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidTransaction(tx.getId(), voidRequest, "admin"));
        IllegalStateException fromPos = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidCashExpense(tx.getId(), voidRequest, "operator"));
        FinancialTransactionRequest edit = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .amount(new BigDecimal("150.00"))
                .description("Otro texto")
                .build();
        IllegalStateException onEdit = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.updateTransaction(tx.getId(), edit, "admin"));

        assertEquals("Los movimientos de una deuda se gestionan desde Deudas.", fromFinances.getMessage());
        assertEquals(fromFinances.getMessage(), fromPos.getMessage());
        assertEquals(fromFinances.getMessage(), onEdit.getMessage());
        assertEquals(FinancialTransactionStatus.ACTIVO, tx.getStatus());
        verify(financialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe consultar gastos de una sesión de caja específica")
    void testGetExpensesByCashSession() {
        UUID sessionId = UUID.randomUUID();
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccount(cajaAccount)
                .amount(new BigDecimal("35.00"))
                .cashSessionId(sessionId)
                .status(FinancialTransactionStatus.ACTIVO)
                .build();

        when(financialTransactionRepository.findByCashSessionIdOrderByTransactionDateDesc(sessionId))
                .thenReturn(List.of(tx));

        List<FinancialTransactionResponse> responses = financialTransactionService.getExpensesByCashSession(sessionId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(sessionId, responses.get(0).getCashSessionId());
        assertEquals(new BigDecimal("35.00"), responses.get(0).getAmount());
    }

    private FinancialTransaction cashExpense(UUID sessionId) {
        return FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccount(bisaAccount)
                .amount(new BigDecimal("100.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .cashSessionId(sessionId)
                .build();
    }

    @Test
    @DisplayName("POS: anula un gasto de la caja abierta restituyendo el saldo")
    void testVoidCashExpenseOpenSession() {
        UUID sessionId = UUID.randomUUID();
        FinancialTransaction tx = cashExpense(sessionId);
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        CashSession session = CashSession.builder().id(sessionId).status(CashSessionStatus.ABIERTA)
                .openingAmount(new BigDecimal("200.00")).totalSalesCash(new BigDecimal("50.00")).build();
        when(cashSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        stubSessionSums(sessionId, "0", "0");
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FinancialTransactionResponse res = financialTransactionService.voidCashExpense(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Gasto duplicado").build(), "operador");

        assertEquals(FinancialTransactionStatus.ANULADO, res.getStatus());
        assertEquals("operador", res.getVoidedBy());
        assertEquals(new BigDecimal("1100.00"), bisaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("250.00"), session.getExpectedCash());
    }

    @Test
    @DisplayName("POS: rechaza anular un gasto de una caja cerrada")
    void testVoidCashExpenseClosedSession() {
        UUID sessionId = UUID.randomUUID();
        FinancialTransaction tx = cashExpense(sessionId);
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(cashSessionRepository.findByIdForUpdate(sessionId))
                .thenReturn(Optional.of(CashSession.builder().id(sessionId).status(CashSessionStatus.CERRADA).build()));

        assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidCashExpense(tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "operador"));
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("POS: rechaza anular un movimiento que no es gasto de caja")
    void testVoidCashExpenseRejectsOtherMovements() {
        FinancialTransaction tx = cashExpense(null);
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidCashExpense(tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "operador"));
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("T-18: el alta guarda el monto con escala 2 HALF_UP y la fecha del servidor")
    void testCreateNormalizesAmountAndServerDate() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.INGRESO_MANUAL)
                .sourceAccountId(bisaAccount.getId())
                .categoryId(incomeCat.getId())
                .amount(new BigDecimal("10.555"))
                .description("Redondeo")
                .build();

        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(incomeCat.getId())).thenReturn(Optional.of(incomeCat));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        Instant before = Instant.now();
        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");
        Instant after = Instant.now();

        assertEquals(new BigDecimal("10.56"), res.getAmount());
        assertEquals(new BigDecimal("1010.56"), bisaAccount.getCurrentBalance());
        assertFalse(res.getTransactionDate().isBefore(before));
        assertFalse(res.getTransactionDate().isAfter(after));
    }

    @Test
    @DisplayName("T-18: rechaza un monto que redondeado a 2 decimales queda en 0.00")
    void testCreateRejectsAmountRoundingToZero() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.INGRESO_MANUAL)
                .sourceAccountId(bisaAccount.getId())
                .categoryId(incomeCat.getId())
                .amount(new BigDecimal("0.004"))
                .description("Casi cero")
                .build();

        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(bisaAccount));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> financialTransactionService.createTransaction(req, "admin"));
        assertTrue(ex.getMessage().contains("mayor a 0.00"));
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any(FinancialTransaction.class));
    }

    @Test
    @DisplayName("T-18: la edición compara el monto normalizado y conserva la fecha original del movimiento")
    void testUpdateNormalizesAmountAndKeepsDate() {
        Instant originalDate = Instant.parse("2026-10-01T15:00:00Z");
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccount(bisaAccount)
                .category(expenseCat)
                .amount(new BigDecimal("100.00"))
                .transactionDate(originalDate)
                .status(FinancialTransactionStatus.ACTIVO)
                .build();

        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(expenseCat.getId())).thenReturn(Optional.of(expenseCat));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        // 100.004 rounds to the stored 100.00, so it is not an amount change
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(bisaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("100.004"))
                .description("Corrección")
                .build();

        FinancialTransactionResponse res = financialTransactionService.updateTransaction(tx.getId(), req, "admin");

        assertEquals(new BigDecimal("100.00"), res.getAmount());
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        assertEquals(originalDate, res.getTransactionDate());
    }

    @Test
    @DisplayName("RG-03: rechaza anular un movimiento sin motivo (vacío, en blanco o sin cuerpo)")
    void testVoidRequiresReason() {
        UUID id = UUID.randomUUID();

        for (VoidFinancialTransactionRequest req : java.util.Arrays.asList(
                null,
                new VoidFinancialTransactionRequest(),
                VoidFinancialTransactionRequest.builder().voidReason("   ").build())) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> financialTransactionService.voidTransaction(id, req, "admin"));
            assertTrue(ex.getMessage().contains("motivo de anulación es obligatorio"));
        }
        verifyNoInteractions(financialTransactionRepository, financialAccountRepository, cashSessionRepository);
    }

    @Test
    @DisplayName("RG-03: el POS rechaza anular un gasto de caja sin motivo")
    void testVoidCashExpenseRequiresReason() {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> financialTransactionService.voidCashExpense(id, new VoidFinancialTransactionRequest(), "operador"));
        assertThrows(IllegalArgumentException.class,
                () -> financialTransactionService.voidCashExpense(id, null, "operador"));
        verifyNoInteractions(financialTransactionRepository, financialAccountRepository, cashSessionRepository);
    }

    @Test
    @DisplayName("T-18: el saldo inicial de una cuenta no se anula")
    void testVoidInitialBalanceForbidden() {
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.SALDO_INICIAL)
                .sourceAccount(bisaAccount)
                .amount(new BigDecimal("1000.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> financialTransactionService.voidTransaction(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "admin"));

        assertTrue(ex.getMessage().contains("saldo inicial"));
        assertEquals(FinancialTransactionStatus.ACTIVO, tx.getStatus());
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        verify(financialAccountRepository, never()).findAllByIdForUpdate(any());
    }

    @Test
    @DisplayName("T-18: el saldo inicial de una cuenta no se edita")
    void testUpdateInitialBalanceForbidden() {
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.SALDO_INICIAL)
                .sourceAccount(bisaAccount)
                .amount(new BigDecimal("1000.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.SALDO_INICIAL)
                .sourceAccountId(bisaAccount.getId())
                .amount(new BigDecimal("5.00"))
                .description("Cambio")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.updateTransaction(tx.getId(), req, "admin"));
        assertTrue(ex.getMessage().contains("saldo inicial"));
        assertEquals(new BigDecimal("1000.00"), tx.getAmount());
    }

    private CashTransferRequest cashTransfer(CashTransferDirection direction, UUID accountId, String amount) {
        return CashTransferRequest.builder()
                .direction(direction)
                .accountId(accountId)
                .amount(new BigDecimal(amount))
                .comment("Depósito al banco")
                .build();
    }

    @Test
    @DisplayName("CAJ-08: el retiro de caja es una transferencia de Caja a la otra cuenta vinculada a la caja abierta")
    void testCashWithdrawalFromPos() {
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(cajaAccount));
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joacoAccount.getId())).thenReturn(Optional.of(joacoAccount));
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        CashSession session = openSession();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(joacoAccount, cajaAccount));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));
        stubSessionSums(session.getId(), "100.00", "0");

        FinancialTransactionResponse res = financialTransactionService.createCashTransfer(
                cashTransfer(CashTransferDirection.RETIRO, joacoAccount.getId(), "100.00"), "operador");

        assertEquals(FinancialTransactionType.TRANSFERENCIA, res.getType());
        assertEquals(cajaAccount.getId(), res.getSourceAccount().getId());
        assertTrue(res.getSourceAccount().isCashAccount());
        assertEquals(joacoAccount.getId(), res.getDestinationAccount().getId());
        assertFalse(res.getDestinationAccount().isCashAccount());
        assertEquals("Retiro de caja a Cuenta Joaco", res.getDescription());
        assertEquals("Depósito al banco", res.getNotes());
        assertEquals(session.getId(), res.getCashSessionId());
        assertEquals(new BigDecimal("400.00"), cajaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("300.00"), joacoAccount.getCurrentBalance());
        assertEquals(new BigDecimal("150.00"), session.getExpectedCash());
    }

    @Test
    @DisplayName("CAJ-08: el ingreso de caja es una transferencia de la otra cuenta hacia Caja y suma al esperado")
    void testCashIncomeFromPos() {
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(cajaAccount));
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joacoAccount.getId())).thenReturn(Optional.of(joacoAccount));
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(joacoAccount.getId())).thenReturn(false);
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        CashSession session = openSession();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(joacoAccount, cajaAccount));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));
        stubSessionSums(session.getId(), "0", "50.00");

        FinancialTransactionResponse res = financialTransactionService.createCashTransfer(
                cashTransfer(CashTransferDirection.INGRESO, joacoAccount.getId(), "50.00"), "operador");

        assertEquals(joacoAccount.getId(), res.getSourceAccount().getId());
        assertEquals(cajaAccount.getId(), res.getDestinationAccount().getId());
        assertEquals("Ingreso a caja desde Cuenta Joaco", res.getDescription());
        assertEquals(new BigDecimal("550.00"), cajaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("150.00"), joacoAccount.getCurrentBalance());
        assertEquals(new BigDecimal("300.00"), session.getExpectedCash());
    }

    @Test
    @DisplayName("CAJ-08: rechaza un ingreso o retiro con la misma cuenta Caja")
    void testCashTransferToCajaItselfRejected() {
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(cajaAccount));

        assertThrows(IllegalArgumentException.class, () -> financialTransactionService.createCashTransfer(
                cashTransfer(CashTransferDirection.RETIRO, cajaAccount.getId(), "10.00"), "operador"));
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-08: sin caja abierta el ingreso o retiro se rechaza sin tocar saldos")
    void testCashTransferWithoutOpenSession() {
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(cajaAccount));
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joacoAccount.getId())).thenReturn(Optional.of(joacoAccount));
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> financialTransactionService.createCashTransfer(
                cashTransfer(CashTransferDirection.RETIRO, joacoAccount.getId(), "10.00"), "operador"));
        assertEquals("Los movimientos de la cuenta Caja requieren una caja abierta.", ex.getMessage());
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-08: un retiro mayor al saldo de Caja se rechaza")
    void testCashWithdrawalInsufficientBalance() {
        when(financialAccountRepository.findByIsCashAccountTrue()).thenReturn(Optional.of(cajaAccount));
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joacoAccount.getId())).thenReturn(Optional.of(joacoAccount));
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(cajaAccount.getId())).thenReturn(true);
        openSession();
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(joacoAccount, cajaAccount));

        assertThrows(IllegalStateException.class, () -> financialTransactionService.createCashTransfer(
                cashTransfer(CashTransferDirection.RETIRO, joacoAccount.getId(), "600.00"), "operador"));
        assertEquals(new BigDecimal("500.00"), cajaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAJ-08: las cuentas para ingreso o retiro excluyen Caja")
    void testCashTransferAccountsExcludeCaja() {
        when(financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc())
                .thenReturn(List.of(cajaAccount, bisaAccount, joacoAccount));

        List<PaymentAccountResponse> accounts = financialTransactionService.getCashTransferAccounts();

        assertEquals(List.of(bisaAccount.getId(), joacoAccount.getId()), accounts.stream().map(PaymentAccountResponse::id).toList());
    }

    @Test
    @DisplayName("CAJ-08: anula desde el POS un retiro de la caja abierta revirtiendo ambos saldos")
    void testVoidCashTransferOpenSession() {
        UUID sessionId = UUID.randomUUID();
        FinancialTransaction tx = FinancialTransaction.builder()
                .id(UUID.randomUUID())
                .type(FinancialTransactionType.TRANSFERENCIA)
                .sourceAccount(cajaAccount)
                .destinationAccount(joacoAccount)
                .amount(new BigDecimal("100.00"))
                .status(FinancialTransactionStatus.ACTIVO)
                .cashSessionId(sessionId)
                .build();
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        CashSession session = CashSession.builder().id(sessionId).status(CashSessionStatus.ABIERTA)
                .openingAmount(new BigDecimal("200.00")).totalSalesCash(new BigDecimal("50.00")).build();
        when(cashSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        stubSessionSums(sessionId, "0", "0");
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FinancialTransactionResponse res = financialTransactionService.voidCashTransfer(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Monto equivocado").build(), "operador");

        assertEquals(FinancialTransactionStatus.ANULADO, res.getStatus());
        assertEquals(new BigDecimal("600.00"), cajaAccount.getCurrentBalance());
        assertEquals(new BigDecimal("100.00"), joacoAccount.getCurrentBalance());
        assertEquals(new BigDecimal("250.00"), session.getExpectedCash());
    }

    @Test
    @DisplayName("CAJ-08: el POS no anula como ingreso o retiro un movimiento que no es transferencia de caja")
    void testVoidCashTransferRejectsOtherMovements() {
        FinancialTransaction tx = cashExpense(UUID.randomUUID());
        when(financialTransactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));

        assertThrows(IllegalStateException.class, () -> financialTransactionService.voidCashTransfer(
                tx.getId(), VoidFinancialTransactionRequest.builder().voidReason("Error").build(), "operador"));
        verify(financialTransactionRepository, never()).save(any());
    }
}
