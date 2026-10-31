package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.*;
import com.akhana.akhana_admin.service.impl.CustomerServiceImpl;
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
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerCollectionRepository customerCollectionRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private FinancialAccountRepository financialAccountRepository;
    @Mock private FinancialCategoryRepository financialCategoryRepository;
    @Mock private FinancialTransactionRepository financialTransactionRepository;

    private CustomerServiceImpl customerService;

    private Customer neida;
    private FinancialAccount caja;
    private FinancialAccount qr;
    private FinancialAccount efectivo;
    private FinancialCategory venta;
    private CashSession openSession;
    private final List<CustomerCollection> collections = new ArrayList<>();
    private final List<Sale> creditSales = new ArrayList<>();

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerRepository, customerCollectionRepository, saleRepository,
            cashSessionRepository, financialAccountRepository, financialCategoryRepository, financialTransactionRepository,
            new ExpectedCashCalculator(financialTransactionRepository));

        neida = Customer.builder().id(UUID.randomUUID()).name("Tía Neida").status(CustomerStatus.ACTIVO).build();
        caja = FinancialAccount.builder().id(UUID.randomUUID()).name("Caja")
            .currentBalance(new BigDecimal("500.00")).isCashAccount(true).isQrAccount(false).build();
        qr = FinancialAccount.builder().id(UUID.randomUUID()).name("Bisa")
            .currentBalance(new BigDecimal("1000.00")).isCashAccount(false).isQrAccount(true).build();
        efectivo = FinancialAccount.builder().id(UUID.randomUUID()).name("Efectivo")
            .currentBalance(new BigDecimal("300.00")).isCashAccount(false).isQrAccount(false).build();
        venta = FinancialCategory.builder().id(UUID.randomUUID()).name("Venta").type(FinancialCategoryType.INGRESO)
            .isSalesDefault(true).build();
        // CAJ-11: a session of 31-10 still open after midnight
        openSession = CashSession.builder().id(UUID.randomUUID()).status(CashSessionStatus.ABIERTA)
            .openingAmount(new BigDecimal("100.00")).totalSalesCash(BigDecimal.ZERO)
            .operationDate(LocalDate.of(2026, 10, 31)).build();

        when(customerRepository.findById(neida.getId())).thenReturn(Optional.of(neida));
        when(customerRepository.findByIdForUpdate(neida.getId())).thenReturn(Optional.of(neida));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
        when(customerRepository.computeBalance(neida.getId())).thenReturn(BigDecimal.ZERO);
        when(customerCollectionRepository.save(any(CustomerCollection.class))).thenAnswer(inv -> {
            CustomerCollection k = inv.getArgument(0);
            if (k.getId() == null) {
                k.setId(UUID.randomUUID());
                collections.add(k);
            }
            return k;
        });
        when(customerCollectionRepository.findByCustomerId(neida.getId())).thenReturn(collections);
        when(saleRepository.findSalesByCustomerId(neida.getId())).thenReturn(creditSales);
        when(financialTransactionRepository.save(any(FinancialTransaction.class))).thenAnswer(inv -> {
            FinancialTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(UUID.randomUUID());
            }
            return tx;
        });
        when(financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()).thenReturn(Optional.of(venta));
        when(financialAccountRepository.existsByIdAndIsCashAccountTrue(caja.getId())).thenReturn(true);
        when(financialAccountRepository.findActiveByIdForUpdate(caja.getId())).thenReturn(Optional.of(caja));
        when(financialAccountRepository.findActiveByIdForUpdate(qr.getId())).thenReturn(Optional.of(qr));
        when(financialAccountRepository.findActiveByIdForUpdate(efectivo.getId())).thenReturn(Optional.of(efectivo));
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(any())).thenReturn(BigDecimal.ZERO);
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(any())).thenReturn(BigDecimal.ZERO);
    }

    private void balance(String amount) {
        when(customerRepository.computeBalance(neida.getId())).thenReturn(new BigDecimal(amount));
    }

    private Sale creditSale(String number, String total, String credit, Instant date) {
        return creditSale(number, total, credit, date, StoreFormat.storeDay(date));
    }

    private Sale creditSale(String number, String total, String credit, Instant date, LocalDate operationDate) {
        Sale s = Sale.builder().id(UUID.randomUUID()).saleNumber(number).saleType(SaleType.AL_COSTO).customer(neida)
            .totalAmount(new BigDecimal(total)).creditAmount(new BigDecimal(credit)).paymentMethod(PaymentMethod.CREDITO)
            .status(SaleStatus.COMPLETADA).createdAt(date).operationDate(operationDate).build();
        creditSales.add(s);
        return s;
    }

    private CustomerCollection collection(FinancialAccount account, String amount, Instant date) {
        CustomerCollection k = CustomerCollection.builder().id(UUID.randomUUID()).customer(neida).account(account)
            .amount(new BigDecimal(amount)).collectionDate(date).operationDate(StoreFormat.storeDay(date)).createdAt(date)
            .status(CustomerCollectionStatus.ACTIVO).build();
        collections.add(k);
        when(customerCollectionRepository.findById(k.getId())).thenReturn(Optional.of(k));
        return k;
    }

    private FinancialTransaction incomeOf(CustomerCollection k, UUID sessionId) {
        FinancialTransaction tx = FinancialTransaction.builder().id(UUID.randomUUID())
            .type(FinancialTransactionType.INGRESO_MANUAL).sourceAccount(k.getAccount()).amount(k.getAmount())
            .status(FinancialTransactionStatus.ACTIVO).customerCollectionId(k.getId()).cashSessionId(sessionId).build();
        when(financialTransactionRepository.findByCustomerCollectionId(k.getId())).thenReturn(Optional.of(tx));
        return tx;
    }

    @Test
    @DisplayName("DEU-05: el nombre del cliente es único sin distinguir mayúsculas, excluyendo eliminados")
    void nameIsUnique() {
        when(customerRepository.existsByNameIgnoreCaseAndStatusNot("tía neida", CustomerStatus.ELIMINADO)).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(new CustomerRequest("tía neida", null, null, null), "admin"))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessage("Ya existe un cliente con el nombre 'tía neida'.");

        CustomerResponse created = customerService.createCustomer(new CustomerRequest("  Otra tienda ", "700", null, null), "admin");
        assertThat(created.name()).isEqualTo("Otra tienda");
        assertThat(created.status()).isEqualTo(CustomerStatus.ACTIVO);
        assertThat(created.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("DEU-05: no se inactiva ni elimina un cliente con saldo por cobrar; con saldo 0 queda Eliminado")
    void customerWithBalanceCannotBeRemoved() {
        balance("80.00");
        assertThatThrownBy(() -> customerService.deleteCustomer(neida.getId(), "admin"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Tía Neida tiene un saldo por cobrar de Bs 80,00.");
        assertThatThrownBy(() -> customerService.updateCustomer(neida.getId(),
                new CustomerRequest("Tía Neida", null, null, CustomerStatus.INACTIVO), "admin"))
            .isInstanceOf(IllegalStateException.class);

        balance("0");
        customerService.deleteCustomer(neida.getId(), "admin");
        assertThat(neida.getStatus()).isEqualTo(CustomerStatus.ELIMINADO);
        assertThat(neida.getDeletedBy()).isEqualTo("admin");
        assertThatThrownBy(() -> customerService.updateCustomer(neida.getId(),
                new CustomerRequest("Tía Neida", null, null, null), "admin"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Un cliente eliminado no se puede editar.");
    }

    @Test
    @DisplayName("DEU-07: el listado trae el saldo por cobrar de cada cliente")
    void listIncludesBalances() {
        when(customerRepository.search(null, "nei")).thenReturn(List.of(neida));
        when(customerRepository.computeBalances()).thenReturn(List.<Object[]>of(new Object[]{neida.getId(), new BigDecimal("579")}));

        List<CustomerResponse> list = customerService.getCustomers(null, " nei ");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).balance()).isEqualByComparingTo("579.00");
        assertThat(list.get(0).balance().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("DEU-06: cobrar Bs 50 en efectivo crea el cobro y un ingreso Venta en Caja vinculado; sube el efectivo esperado")
    void cashCollectionCreatesIncomeInCaja() {
        balance("80.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.of(openSession));
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(openSession.getId())).thenReturn(new BigDecimal("50.00"));

        customerService.registerCollection(neida.getId(), new CustomerCollectionRequest(caja.getId(), new BigDecimal("50"), " A cuenta "), "cajero");

        assertThat(collections).hasSize(1);
        CustomerCollection k = collections.get(0);
        assertThat(k.getAmount()).isEqualByComparingTo("50.00");
        assertThat(k.getComment()).isEqualTo("A cuenta");
        ArgumentCaptor<FinancialTransaction> txCaptor = ArgumentCaptor.forClass(FinancialTransaction.class);
        verify(financialTransactionRepository).save(txCaptor.capture());
        FinancialTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(FinancialTransactionType.INGRESO_MANUAL);
        assertThat(tx.getCategory()).isSameAs(venta);
        assertThat(tx.getDescription()).isEqualTo("Cobro a Tía Neida");
        assertThat(tx.getCustomerCollectionId()).isEqualTo(k.getId());
        assertThat(tx.getCashSessionId()).isEqualTo(openSession.getId());
        // CAJ-11: into Caja, the collection and its income count for the day of the session
        assertThat(k.getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(tx.getOperationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("550.00");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("DEU-06: por QR entra a la cuenta QR sin caja; otra cuenta se rechaza")
    void qrCollectionAndOtherAccounts() {
        balance("80.00");
        customerService.registerCollection(neida.getId(), new CustomerCollectionRequest(qr.getId(), new BigDecimal("30.00"), null), "cajero");
        assertThat(qr.getCurrentBalance()).isEqualByComparingTo("1030.00");
        verify(cashSessionRepository, never()).findByStatusForUpdate(any());
        // CAJ-11: into QR, without a session, it counts for the day it is recorded
        assertThat(collections.get(0).getOperationDate()).isEqualTo(StoreFormat.storeDay(collections.get(0).getCollectionDate()));

        assertThatThrownBy(() -> customerService.registerCollection(neida.getId(),
                new CustomerCollectionRequest(efectivo.getId(), new BigDecimal("10.00"), null), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Los cobros entran a Caja o a la cuenta QR.");
        assertThat(efectivo.getCurrentBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("DEU-06: a Caja sin caja abierta se rechaza; el cobro no supera el saldo ni es 0")
    void collectionRejections() {
        balance("30.00");
        when(cashSessionRepository.findByStatusForUpdate(CashSessionStatus.ABIERTA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.registerCollection(neida.getId(),
                new CustomerCollectionRequest(caja.getId(), new BigDecimal("10.00"), null), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Los movimientos de la cuenta Caja requieren una caja abierta.");
        assertThatThrownBy(() -> customerService.registerCollection(neida.getId(),
                new CustomerCollectionRequest(qr.getId(), new BigDecimal("30.01"), null), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El cobro supera el saldo por cobrar de Tía Neida (Bs 30,00).");
        assertThatThrownBy(() -> customerService.registerCollection(neida.getId(),
                new CustomerCollectionRequest(qr.getId(), new BigDecimal("0.004"), null), "cajero"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(collections).isEmpty();
    }

    @Test
    @DisplayName("DEU-06: anular un cobro de Caja anula su ingreso, baja la cuenta y el efectivo esperado")
    void voidCashCollection() {
        CustomerCollection k = collection(caja, "50.00", Instant.now());
        FinancialTransaction tx = incomeOf(k, openSession.getId());
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(openSession));
        when(financialAccountRepository.findAllByIdForUpdate(any())).thenReturn(List.of(caja));

        customerService.voidCollection(neida.getId(), k.getId(), new VoidDebtRequest(" Error de monto "), "cajero");

        assertThat(k.getStatus()).isEqualTo(CustomerCollectionStatus.ANULADO);
        assertThat(k.getVoidReason()).isEqualTo("Error de monto");
        assertThat(tx.getStatus()).isEqualTo(FinancialTransactionStatus.ANULADO);
        assertThat(caja.getCurrentBalance()).isEqualByComparingTo("450.00");
        assertThat(openSession.getExpectedCash()).isEqualByComparingTo("100.00");

        assertThatThrownBy(() -> customerService.voidCollection(neida.getId(), k.getId(), new VoidDebtRequest("Otra"), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("El cobro ya está anulado.");
    }

    @Test
    @DisplayName("DEU-06: un cobro de una caja cerrada no se anula; el motivo es obligatorio")
    void voidCollectionRejections() {
        CustomerCollection k = collection(caja, "50.00", Instant.now());
        FinancialTransaction tx = incomeOf(k, openSession.getId());
        openSession.setStatus(CashSessionStatus.CERRADA);
        when(cashSessionRepository.findByIdForUpdate(openSession.getId())).thenReturn(Optional.of(openSession));

        assertThatThrownBy(() -> customerService.voidCollection(neida.getId(), k.getId(), new VoidDebtRequest("Error"), "cajero"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("El cobro se hizo en una caja cerrada; no se puede anular.");
        assertThat(tx.getStatus()).isEqualTo(FinancialTransactionStatus.ACTIVO);
        assertThat(k.getStatus()).isEqualTo(CustomerCollectionStatus.ACTIVO);

        assertThatThrownBy(() -> customerService.voidCollection(neida.getId(), k.getId(), new VoidDebtRequest(" "), "cajero"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El motivo de anulación es obligatorio.");
    }

    @Test
    @DisplayName("DEU-07: ventas a crédito y cobros en orden de fecha con el saldo después de cada uno; anulados no cuentan")
    void historyWithRunningBalance() {
        Instant d1 = Instant.parse("2026-10-22T15:00:00Z");
        creditSale("VTA-000100", "579.00", "579.00", d1);
        Sale voided = creditSale("VTA-000101", "120.00", "80.00", d1.plusSeconds(3600));
        voided.setStatus(SaleStatus.ANULADA);
        voided.setVoidReason("Error");
        collection(caja, "500.00", d1.plusSeconds(86400));
        CustomerCollection voidedCollection = collection(qr, "40.00", d1.plusSeconds(90000));
        voidedCollection.setStatus(CustomerCollectionStatus.ANULADO);

        CustomerHistoryResponse history = customerService.getHistory(neida.getId());

        assertThat(history.entries()).extracting(CustomerHistoryResponse.Entry::type).containsExactly(
            CustomerHistoryResponse.EntryType.SALE, CustomerHistoryResponse.EntryType.SALE,
            CustomerHistoryResponse.EntryType.COLLECTION, CustomerHistoryResponse.EntryType.COLLECTION);
        assertThat(history.entries()).extracting(CustomerHistoryResponse.Entry::balanceAfter)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("579.00"), new BigDecimal("579.00"), new BigDecimal("79.00"), new BigDecimal("79.00"));
        assertThat(history.entries().get(1).voided()).isTrue();
        assertThat(history.entries().get(0).saleNumber()).isEqualTo("VTA-000100");
        assertThat(history.entries().get(2).accountName()).isEqualTo("Caja");
        assertThat(history.customer().balance()).isEqualByComparingTo("79.00");
    }

    @Test
    @DisplayName("CAJ-11: una venta al costo cargada pasada la medianoche se ve en el día de su caja; el saldo no cambia")
    void historyByDayOfOperation() {
        // A collection at 23:00 of 31-10 and a credit sale of the session of 31-10 recorded at 00:20 of 01-11
        collection(qr, "10.00", Instant.parse("2026-11-01T03:00:00Z"));
        creditSale("VTA-000300", "50.00", "50.00", Instant.parse("2026-11-01T04:20:00Z"), LocalDate.of(2026, 10, 31));
        creditSale("VTA-000301", "20.00", "20.00", Instant.parse("2026-11-01T14:00:00Z"));

        CustomerHistoryResponse history = customerService.getHistory(neida.getId());

        assertThat(history.entries()).extracting(CustomerHistoryResponse.Entry::operationDate).containsExactly(
            LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1));
        assertThat(history.entries().get(1).saleNumber()).isEqualTo("VTA-000300");
        assertThat(history.entries().get(1).date()).isEqualTo(Instant.parse("2026-11-01T04:20:00Z"));
        assertThat(history.customer().balance()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("P-49: la venta al costo pagada en el momento aparece en el historial sin cambiar el saldo")
    void paidAtCostSaleInHistory() {
        Instant d1 = Instant.parse("2026-10-22T15:00:00Z");
        creditSale("VTA-000100", "100.00", "100.00", d1);
        creditSale("VTA-000102", "25.00", "0.00", d1.plusSeconds(3600)).setPaymentMethod(PaymentMethod.EFECTIVO);

        CustomerHistoryResponse history = customerService.getHistory(neida.getId());

        assertThat(history.entries()).hasSize(2);
        CustomerHistoryResponse.Entry paid = history.entries().get(1);
        assertThat(paid.amount()).isEqualByComparingTo("0.00");
        assertThat(paid.saleTotal()).isEqualByComparingTo("25.00");
        assertThat(paid.balanceAfter()).isEqualByComparingTo("100.00");
        assertThat(history.customer().balance()).isEqualByComparingTo("100.00");
    }
}
