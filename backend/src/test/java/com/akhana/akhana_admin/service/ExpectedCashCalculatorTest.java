package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.model.CashSession;
import com.akhana.akhana_admin.model.CashSessionStatus;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.impl.ExpectedCashCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpectedCashCalculatorTest {

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @InjectMocks
    private ExpectedCashCalculator calculator;

    private CashSession session;

    @BeforeEach
    void setUp() {
        session = CashSession.builder()
                .id(UUID.randomUUID())
                .status(CashSessionStatus.ABIERTA)
                .openingAmount(new BigDecimal("200.00"))
                .totalSalesCash(new BigDecimal("850.00"))
                .totalSalesQr(new BigDecimal("300.00"))
                .build();
    }

    private void sums(String outflows, String inflows) {
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(session.getId())).thenReturn(new BigDecimal(outflows));
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(session.getId())).thenReturn(new BigDecimal(inflows));
    }

    @Test
    @DisplayName("Sin movimientos: inicial + ventas en efectivo (las ventas QR no cuentan)")
    void openingPlusCashSales() {
        sums("0", "0");
        assertEquals(new BigDecimal("1050.00"), calculator.calculate(session));
    }

    @Test
    @DisplayName("Gastos y transferencias desde Caja restan")
    void outflowsSubtract() {
        // 120 de gastos + 300 transferidos a Efectivo
        sums("420.00", "0");
        assertEquals(new BigDecimal("630.00"), calculator.calculate(session));
    }

    @Test
    @DisplayName("Transferencias hacia Caja suman")
    void inflowsAdd() {
        sums("0", "150.00");
        assertEquals(new BigDecimal("1200.00"), calculator.calculate(session));
    }

    @Test
    @DisplayName("Transferencias en ambos sentidos y gastos a la vez")
    void bothDirections() {
        sums("170.50", "80.25");
        assertEquals(new BigDecimal("959.75"), calculator.calculate(session));
    }

    @Test
    @DisplayName("Sumas nulas y totales nulos cuentan como cero; refresh guarda el valor en la caja")
    void nullsAreZeroAndRefreshStores() {
        session.setTotalSalesCash(null);
        when(financialTransactionRepository.sumActiveCashOutflowsBySessionId(session.getId())).thenReturn(null);
        when(financialTransactionRepository.sumActiveCashInflowsBySessionId(session.getId())).thenReturn(null);

        assertEquals(new BigDecimal("200.00"), calculator.refresh(session));
        assertEquals(new BigDecimal("200.00"), session.getExpectedCash());
    }
}
