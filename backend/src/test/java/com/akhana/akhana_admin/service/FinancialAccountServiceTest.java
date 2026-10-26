package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialAccountRequest;
import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.OpeningBalanceRequest;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.model.FinancialTransaction;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.repository.CashSessionRepository;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.impl.FinancialAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialAccountServiceTest {

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private CashSessionRepository cashSessionRepository;

    @InjectMocks
    private FinancialAccountServiceImpl financialAccountService;

    private FinancialAccount bisaAccount;
    private FinancialAccount cajaAccount;

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
                .currentBalance(BigDecimal.ZERO)
                .isCashAccount(true)
                .isQrAccount(false)
                .build();
    }

    @Test
    @DisplayName("Debe listar todas las cuentas no eliminadas")
    void testGetAllAccounts() {
        when(financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc())
                .thenReturn(List.of(bisaAccount, cajaAccount));

        List<FinancialAccountResponse> list = financialAccountService.getAllAccounts();

        assertEquals(2, list.size());
        assertEquals("Cuenta bancaria BISA", list.get(0).getName());
    }

    @Test
    @DisplayName("Debe crear una cuenta y registrar su saldo inicial en transacciones")
    void testCreateAccount() {
        FinancialAccountRequest request = FinancialAccountRequest.builder()
                .name("Cuenta Nueva")
                .color("#123456")
                .initialBalance(new BigDecimal("500.00"))
                .isQrAccount(false)
                .build();

        when(financialAccountRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Cuenta Nueva")).thenReturn(false);
        when(financialAccountRepository.save(any(FinancialAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialAccountResponse response = financialAccountService.createAccount(request, "admin");

        assertNotNull(response);
        assertEquals("Cuenta Nueva", response.getName());
        assertEquals(new BigDecimal("500.00"), response.getCurrentBalance());
        verify(financialTransactionRepository, times(1)).save(any(FinancialTransaction.class));
    }

    @Test
    @DisplayName("T-18: el saldo inicial se guarda con escala 2 HALF_UP en la cuenta y en su movimiento")
    void testCreateAccountNormalizesInitialBalance() {
        FinancialAccountRequest request = FinancialAccountRequest.builder()
                .name("Cuenta Redondeo")
                .initialBalance(new BigDecimal("100.005"))
                .build();

        when(financialAccountRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Cuenta Redondeo")).thenReturn(false);
        when(financialAccountRepository.save(any(FinancialAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialAccountResponse response = financialAccountService.createAccount(request, "admin");

        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(txCaptor.capture());
        assertEquals(new BigDecimal("100.01"), response.getCurrentBalance());
        assertEquals(new BigDecimal("100.01"), response.getInitialBalance());
        assertEquals(new BigDecimal("100.01"), txCaptor.getValue().getAmount());
    }

    @Test
    @DisplayName("T-18: sin saldo inicial, la cuenta arranca en 0.00")
    void testCreateAccountWithoutInitialBalance() {
        FinancialAccountRequest request = FinancialAccountRequest.builder().name("Cuenta Cero").build();

        when(financialAccountRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Cuenta Cero")).thenReturn(false);
        when(financialAccountRepository.save(any(FinancialAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialAccountResponse response = financialAccountService.createAccount(request, "admin");

        assertEquals(new BigDecimal("0.00"), response.getCurrentBalance());
    }

    @Test
    @DisplayName("Debe impedir crear cuenta con nombre duplicado")
    void testCreateAccountDuplicate() {
        FinancialAccountRequest request = FinancialAccountRequest.builder()
                .name("Cuenta bancaria BISA")
                .build();

        when(financialAccountRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Cuenta bancaria BISA")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> financialAccountService.createAccount(request, "admin"));
    }

    @Test
    @DisplayName("Debe permitir cambiar cuenta QR transfiriendo la configuración")
    void testSetQrAccount() {
        FinancialAccount joaco = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Cuenta Joaco")
                .isQrAccount(false)
                .build();

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joaco.getId())).thenReturn(Optional.of(joaco));
        when(financialAccountRepository.findByIsQrAccountTrueAndDeletedAtIsNull()).thenReturn(Optional.of(bisaAccount));
        when(financialAccountRepository.save(any(FinancialAccount.class))).thenAnswer(i -> i.getArgument(0));

        FinancialAccountResponse res = financialAccountService.setQrAccount(joaco.getId(), "admin");

        assertTrue(res.getIsQrAccount());
        assertFalse(bisaAccount.getIsQrAccount());
    }

    @Test
    @DisplayName("Debe impedir eliminar cuenta Caja")
    void testDeleteCajaForbidden() {
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(cajaAccount.getId())).thenReturn(Optional.of(cajaAccount));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialAccountService.deleteAccount(cajaAccount.getId(), "admin"));
        assertTrue(ex.getMessage().contains("La cuenta Caja es fija"));
    }

    @Test
    @DisplayName("Debe impedir eliminar cuenta QR")
    void testDeleteQrAccountForbidden() {
        bisaAccount.setCurrentBalance(BigDecimal.ZERO);
        when(financialAccountRepository.findByIdAndDeletedAtIsNull(bisaAccount.getId())).thenReturn(Optional.of(bisaAccount));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialAccountService.deleteAccount(bisaAccount.getId(), "admin"));
        assertTrue(ex.getMessage().contains("cobros QR no puede eliminarse"));
    }

    @Test
    @DisplayName("Debe impedir eliminar cuenta con saldo distinto de cero")
    void testDeleteNonZeroBalanceForbidden() {
        FinancialAccount joaco = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Cuenta Joaco")
                .currentBalance(new BigDecimal("150.00"))
                .isCashAccount(false)
                .isQrAccount(false)
                .build();

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joaco.getId())).thenReturn(Optional.of(joaco));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialAccountService.deleteAccount(joaco.getId(), "admin"));
        assertTrue(ex.getMessage().contains("saldo actual sea exactamente 0.00 Bs"));
    }

    @Test
    @DisplayName("Debe eliminar lógicamente una cuenta con saldo cero y no protegida")
    void testDeleteAccountSuccess() {
        FinancialAccount joaco = FinancialAccount.builder()
                .id(UUID.randomUUID())
                .name("Cuenta Joaco")
                .currentBalance(BigDecimal.ZERO)
                .isCashAccount(false)
                .isQrAccount(false)
                .build();

        when(financialAccountRepository.findByIdAndDeletedAtIsNull(joaco.getId())).thenReturn(Optional.of(joaco));

        financialAccountService.deleteAccount(joaco.getId(), "admin");

        assertNotNull(joaco.getDeletedAt());
        assertEquals("admin", joaco.getDeletedBy());
        verify(financialAccountRepository).save(joaco);
    }

    @Test
    @DisplayName("FIN-01: el saldo al corte reemplaza el saldo inicial de una cuenta sin otros movimientos")
    void setOpeningBalance_updatesInitialMovementAndBalances() {
        bisaAccount.setInitialBalance(new BigDecimal("0.00"));
        bisaAccount.setCurrentBalance(new BigDecimal("0.00"));
        FinancialTransaction openingTx = FinancialTransaction.builder()
                .type(FinancialTransactionType.SALDO_INICIAL)
                .sourceAccount(bisaAccount)
                .amount(new BigDecimal("0.00"))
                .createdBy("SYSTEM")
                .build();
        when(financialAccountRepository.findActiveByIdForUpdate(bisaAccount.getId())).thenReturn(Optional.of(bisaAccount));
        when(financialTransactionRepository.existsMovementsBesidesOpeningBalance(bisaAccount.getId())).thenReturn(false);
        when(financialTransactionRepository.findFirstBySourceAccountIdAndType(bisaAccount.getId(), FinancialTransactionType.SALDO_INICIAL))
                .thenReturn(Optional.of(openingTx));
        when(financialAccountRepository.save(any(FinancialAccount.class))).thenAnswer(i -> i.getArgument(0));

        FinancialAccountResponse res = financialAccountService.setOpeningBalance(
                bisaAccount.getId(), new OpeningBalanceRequest(new BigDecimal("31562.575")), "admin");

        assertEquals(new BigDecimal("31562.58"), res.getInitialBalance());
        assertEquals(new BigDecimal("31562.58"), res.getCurrentBalance());
        assertEquals(new BigDecimal("31562.58"), openingTx.getAmount());
        assertEquals("Saldo al corte", openingTx.getDescription());
        assertEquals("admin", openingTx.getUpdatedBy());
        assertEquals("SYSTEM", openingTx.getCreatedBy());
        verify(financialTransactionRepository).save(openingTx);
    }

    @Test
    @DisplayName("FIN-01: una cuenta con movimientos no admite saldo al corte")
    void setOpeningBalance_withMovements_isRejected() {
        when(financialAccountRepository.findActiveByIdForUpdate(bisaAccount.getId())).thenReturn(Optional.of(bisaAccount));
        when(financialTransactionRepository.existsMovementsBesidesOpeningBalance(bisaAccount.getId())).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialAccountService.setOpeningBalance(bisaAccount.getId(), new OpeningBalanceRequest(new BigDecimal("10")), "admin"));

        assertTrue(ex.getMessage().contains("ya tiene movimientos"));
        assertEquals(new BigDecimal("1000.00"), bisaAccount.getCurrentBalance());
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("FIN-01: el saldo al corte de Caja se rechaza si ya se abrió una caja")
    void setOpeningBalance_cashAfterFirstSession_isRejected() {
        when(financialAccountRepository.findActiveByIdForUpdate(cajaAccount.getId())).thenReturn(Optional.of(cajaAccount));
        when(cashSessionRepository.count()).thenReturn(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialAccountService.setOpeningBalance(cajaAccount.getId(), new OpeningBalanceRequest(new BigDecimal("7637.90")), "admin"));

        assertTrue(ex.getMessage().contains("primera apertura"));
        verify(financialTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("FIN-01: el listado indica si la cuenta todavía admite saldo al corte")
    void getAllAccounts_reportsOpeningBalanceEditable() {
        when(financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc()).thenReturn(List.of(bisaAccount, cajaAccount));
        when(financialTransactionRepository.existsMovementsBesidesOpeningBalance(bisaAccount.getId())).thenReturn(true);
        when(cashSessionRepository.count()).thenReturn(0L);
        when(financialTransactionRepository.existsMovementsBesidesOpeningBalance(cajaAccount.getId())).thenReturn(false);

        List<FinancialAccountResponse> list = financialAccountService.getAllAccounts();

        assertFalse(list.get(0).getOpeningBalanceEditable());
        assertTrue(list.get(1).getOpeningBalanceEditable());
    }
}
