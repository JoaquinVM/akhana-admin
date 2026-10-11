package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialAccountRequest;
import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.model.FinancialTransaction;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.impl.FinancialAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
