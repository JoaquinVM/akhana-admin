package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.model.FinancialCategory;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import com.akhana.akhana_admin.model.FinancialTransaction;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.impl.FinancialSummaryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialSummaryServiceTest {

    @Mock
    private FinancialAccountService financialAccountService;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @InjectMocks
    private FinancialSummaryServiceImpl financialSummaryService;

    @Test
    @DisplayName("Debe calcular KPIs consolidados de saldos de cuentas y detectar cuenta QR")
    void testGetSummary() {
        FinancialAccountResponse caja = FinancialAccountResponse.builder()
                .id(UUID.randomUUID())
                .name("Caja")
                .currentBalance(new BigDecimal("350.00"))
                .isCashAccount(true)
                .isQrAccount(false)
                .build();

        FinancialAccountResponse bisa = FinancialAccountResponse.builder()
                .id(UUID.randomUUID())
                .name("Cuenta bancaria BISA")
                .currentBalance(new BigDecimal("1200.00"))
                .isCashAccount(false)
                .isQrAccount(true)
                .build();

        FinancialAccountResponse joaco = FinancialAccountResponse.builder()
                .id(UUID.randomUUID())
                .name("Cuenta Joaco")
                .currentBalance(new BigDecimal("500.00"))
                .isCashAccount(false)
                .isQrAccount(false)
                .build();

        when(financialAccountService.getAllAccounts()).thenReturn(List.of(bisa, caja, joaco));
        when(financialTransactionRepository.findTop10ByOrderByTransactionDateDesc())
                .thenReturn(List.of());

        FinancialSummaryResponse summary = financialSummaryService.getSummary();

        assertNotNull(summary);
        assertEquals(new BigDecimal("350.00"), summary.getCashBalance());
        // FIN-09: every account with its balance, whatever its name (no fixed Bisa or Joaco fields)
        assertEquals(3, summary.getAccounts().size());
        assertEquals(new BigDecimal("500.00"), summary.getAccounts().stream()
                .filter(a -> a.getId().equals(joaco.getId())).findFirst().orElseThrow().getCurrentBalance());
        assertEquals(new BigDecimal("2050.00"), summary.getTotalBalance());
        assertEquals(bisa.getId(), summary.getQrAccountId());
        assertEquals("Cuenta bancaria BISA", summary.getQrAccountName());
    }

    @Test
    @DisplayName("FIN-09: el resumen trae las cuentas y los movimientos con los mismos campos que sus pantallas")
    void keepsEveryFieldOfAccountsAndMovements() {
        FinancialAccountResponse account = FinancialAccountResponse.builder()
                .id(UUID.randomUUID()).name("Caja").currentBalance(new BigDecimal("80.00"))
                .isCashAccount(true).isQrAccount(false).openingBalanceEditable(false).build();
        when(financialAccountService.getAllAccounts()).thenReturn(List.of(account));
        FinancialAccount caja = FinancialAccount.builder().id(account.getId()).name("Caja").color("#16a34a")
                .isCashAccount(true).build();
        FinancialAccount banco = FinancialAccount.builder().id(UUID.randomUUID()).name("Banco").color("#2563eb")
                .isCashAccount(false).build();
        FinancialCategory pedido = FinancialCategory.builder().id(UUID.randomUUID()).name("Pedido")
                .type(FinancialCategoryType.GASTO).color("#dc2626").build();
        FinancialTransaction purchasePayment = FinancialTransaction.builder()
                .id(UUID.randomUUID()).type(FinancialTransactionType.GASTO_MANUAL).amount(new BigDecimal("40.00"))
                .transactionDate(Instant.parse("2026-11-20T15:00:00Z")).operationDate(LocalDate.of(2026, 11, 20))
                .description("Pago de compra").sourceAccount(caja).destinationAccount(banco).category(pedido)
                .purchaseId(UUID.randomUUID()).creditorRepaymentId(UUID.randomUUID())
                .customerCollectionId(UUID.randomUUID()).cashSessionId(UUID.randomUUID())
                .historicalImportId(UUID.randomUUID()).build();
        when(financialTransactionRepository.findTop10ByOrderByTransactionDateDesc())
                .thenReturn(List.of(purchasePayment));

        FinancialSummaryResponse summary = financialSummaryService.getSummary();

        // The accounts are the accounts screen's, with whether the opening balance can still be edited
        assertSame(account, summary.getAccounts().getFirst());
        assertEquals(false, summary.getAccounts().getFirst().getOpeningBalanceEditable());
        FinancialTransactionResponse tx = summary.getRecentTransactions().getFirst();
        assertEquals(purchasePayment.getPurchaseId(), tx.getPurchaseId());
        assertEquals(purchasePayment.getCreditorRepaymentId(), tx.getCreditorRepaymentId());
        assertEquals(purchasePayment.getCustomerCollectionId(), tx.getCustomerCollectionId());
        assertEquals(purchasePayment.getCashSessionId(), tx.getCashSessionId());
        assertTrue(tx.isHistorical());
        assertTrue(tx.getSourceAccount().isCashAccount());
        assertFalse(tx.getDestinationAccount().isCashAccount());
        assertEquals("#dc2626", tx.getCategory().getColor());
        assertEquals(new BigDecimal("80.00"), summary.getCashBalance());
    }
}
