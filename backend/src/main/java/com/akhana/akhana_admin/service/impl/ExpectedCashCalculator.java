package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.model.CashSession;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single place for the expected cash of a session (CAJ-03, T-15):
 * opening + cash sales + collections into Caja − Caja expenses − transfers out of Caja + transfers into Caja.
 * An at-cost sale on credit only adds its cash part (DEU-05).
 * Voided movements are excluded by the repository sums.
 */
@Component
@RequiredArgsConstructor
public class ExpectedCashCalculator {

    private final FinancialTransactionRepository financialTransactionRepository;

    public BigDecimal calculate(CashSession session) {
        BigDecimal outflows = financialTransactionRepository.sumActiveCashOutflowsBySessionId(session.getId());
        BigDecimal inflows = financialTransactionRepository.sumActiveCashInflowsBySessionId(session.getId());
        return orZero(session.getOpeningAmount())
            .add(orZero(session.getTotalSalesCash()))
            .add(orZero(inflows))
            .subtract(orZero(outflows))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /** Recalculates and stores the expected cash on the (already locked) session. */
    public BigDecimal refresh(CashSession session) {
        BigDecimal expected = calculate(session);
        session.setExpectedCash(expected);
        return expected;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
