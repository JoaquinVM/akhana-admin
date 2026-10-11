package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.dto.VoidFinancialTransactionRequest;
import com.akhana.akhana_admin.model.FinancialTransactionStatus;
import com.akhana.akhana_admin.model.FinancialTransactionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FinancialTransactionService {

    List<FinancialTransactionResponse> getTransactions(
            Instant startDate,
            Instant endDate,
            FinancialTransactionType type,
            UUID accountId,
            UUID categoryId,
            FinancialTransactionStatus status,
            String search
    );

    List<FinancialTransactionResponse> getAccountTransactions(
            UUID accountId,
            Instant startDate,
            Instant endDate
    );

    List<FinancialTransactionResponse> getExpensesByCashSession(UUID cashSessionId);

    FinancialTransactionResponse getTransactionById(UUID id);

    FinancialTransactionResponse createTransaction(FinancialTransactionRequest request, String currentUsername);

    FinancialTransactionResponse updateTransaction(UUID id, FinancialTransactionRequest request, String currentUsername);

    FinancialTransactionResponse voidTransaction(UUID id, VoidFinancialTransactionRequest request, String currentUsername);
}
