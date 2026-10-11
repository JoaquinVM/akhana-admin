package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.FinancialTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class FinancialTransactionRepositoryTest {

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Test
    void testFilterTransactionsWithNullSearch() {
        Instant now = Instant.now();
        List<FinancialTransaction> result = financialTransactionRepository.filterTransactions(
                now.minusSeconds(86400 * 30),
                now,
                null,
                null,
                null,
                null,
                null
        );
        assertNotNull(result);
    }

    @Test
    void testFilterTransactionsWithNonNullSearch() {
        Instant now = Instant.now();
        List<FinancialTransaction> result = financialTransactionRepository.filterTransactions(
                now.minusSeconds(86400 * 30),
                now,
                null,
                null,
                null,
                null,
                "venta"
        );
        assertNotNull(result);
    }
}
