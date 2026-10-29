package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.impl.FinancialSummaryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialSummaryServiceTest {

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @InjectMocks
    private FinancialSummaryServiceImpl financialSummaryService;

    @Test
    @DisplayName("Debe calcular KPIs consolidados de saldos de cuentas y detectar cuenta QR")
    void testGetSummary() {
        FinancialAccount caja = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Caja")
                .currentBalance(new BigDecimal("350.00"))
                .isCashAccount(true)
                .isQrAccount(false)
                .build();

        FinancialAccount bisa = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Cuenta bancaria BISA")
                .currentBalance(new BigDecimal("1200.00"))
                .isCashAccount(false)
                .isQrAccount(true)
                .build();

        FinancialAccount joaco = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Cuenta Joaco")
                .currentBalance(new BigDecimal("500.00"))
                .isCashAccount(false)
                .isQrAccount(false)
                .build();

        when(financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc())
                .thenReturn(List.of(bisa, caja, joaco));
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
}
