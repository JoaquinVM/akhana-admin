package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Active movements per store month and category (FIN-10): incomes (+) and expenses (-) with their net. Transfers
 * and consumptions at cost (CON-03) go apart and never add to the net; opening balances are left out.
 */
public record TransactionsByCategoryReportResponse(LocalDate from, LocalDate to, List<Month> months, Month totals) {

    /**
     * One month ("2026-08"), or the sum of the range when {@code month} is null. Expense amounts are negative and
     * {@code net} = incomes + expenses. {@code consumptionTotal} is positive and moves no money.
     */
    public record Month(String month,
                        List<CategoryRow> incomes,
                        BigDecimal incomeTotal,
                        List<CategoryRow> expenses,
                        BigDecimal expenseTotal,
                        BigDecimal net,
                        List<TransferRow> transfers,
                        long consumptions,
                        BigDecimal consumptionTotal) {
    }

    /** A category and its signed sum; {@code categoryId} is null for movements without one. */
    public record CategoryRow(UUID categoryId, String categoryName, BigDecimal amount) {
    }

    /** The transfers of one month (or the range) from one account to another. */
    public record TransferRow(UUID sourceAccountId,
                              String sourceAccountName,
                              UUID destinationAccountId,
                              String destinationAccountName,
                              BigDecimal amount) {
    }
}
