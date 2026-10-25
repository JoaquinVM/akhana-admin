package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.dto.VoidFinancialTransactionRequest;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.impl.FinancialTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private FinancialTransactionServiceImpl financialTransactionService;

    private FinancialAccount bisaAccount;
    private FinancialAccount cajaAccount;
    private FinancialAccount joacoAccount;
    private FinancialCategory incomeCat;
    private FinancialCategory expenseCat;

    @BeforeEach
    void setUp() {
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

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(bisaAccount.getId())).thenReturn(Optional.of(bisaAccount));
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

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(cajaAccount.getId())).thenReturn(Optional.of(cajaAccount));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.createTransaction(req, "admin"));
        assertTrue(ex.getMessage().contains("cuenta Caja"));
    }

    @Test
    @DisplayName("Debe permitir registrar gasto manual en cuenta Caja deduciendo su saldo")
    void testCreateExpenseInCaja() {
        FinancialTransactionRequest req = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cajaAccount.getId())
                .categoryId(expenseCat.getId())
                .amount(new BigDecimal("50.00"))
                .description("Compra de café y azúcar")
                .build();

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(cajaAccount.getId())).thenReturn(Optional.of(cajaAccount));
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(expenseCat.getId())).thenReturn(Optional.of(expenseCat));
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FinancialTransactionResponse res = financialTransactionService.createTransaction(req, "admin");

        assertNotNull(res);
        assertEquals(new BigDecimal("450.00"), cajaAccount.getCurrentBalance());
        verify(financialAccountRepository).save(cajaAccount);
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

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(bisaAccount.getId())).thenReturn(Optional.of(bisaAccount));
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joacoAccount.getId())).thenReturn(Optional.of(joacoAccount));
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

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(bisaAccount.getId())).thenReturn(Optional.of(bisaAccount));
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joacoAccount.getId())).thenReturn(Optional.of(joacoAccount));

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

        when(financialTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
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

        when(financialTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidTransaction(tx.getId(), new VoidFinancialTransactionRequest(), "admin"));
        assertTrue(ex.getMessage().contains("generadas por ventas no pueden anularse desde Finanzas"));
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
        when(financialTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        CashSession session = CashSession.builder().id(sessionId).status(CashSessionStatus.ABIERTA)
                .openingAmount(new BigDecimal("200.00")).totalSalesCash(new BigDecimal("50.00")).build();
        when(cashSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(financialTransactionRepository.sumActiveCashExpensesBySessionId(sessionId)).thenReturn(BigDecimal.ZERO);
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
        when(financialTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        when(cashSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(CashSession.builder().id(sessionId).status(CashSessionStatus.CERRADA).build()));

        assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidCashExpense(tx.getId(), new VoidFinancialTransactionRequest(), "operador"));
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("POS: rechaza anular un movimiento que no es gasto de caja")
    void testVoidCashExpenseRejectsOtherMovements() {
        FinancialTransaction tx = cashExpense(null);
        when(financialTransactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        assertThrows(IllegalStateException.class,
                () -> financialTransactionService.voidCashExpense(tx.getId(), new VoidFinancialTransactionRequest(), "operador"));
        verify(financialTransactionRepository, never()).save(any());
    }
}
