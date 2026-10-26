package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.CashTransferRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.PaymentAccountResponse;
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

    /** Voids a cash expense of the open cash session (POS screen). */
    FinancialTransactionResponse voidCashExpense(UUID id, VoidFinancialTransactionRequest request, String currentUsername);

    /** Active accounts other than Caja, for cash transfers from the POS (CAJ-08). */
    List<PaymentAccountResponse> getCashTransferAccounts();

    /** Registers a cash income or withdrawal as a transfer between Caja and another account (CAJ-08). */
    FinancialTransactionResponse createCashTransfer(CashTransferRequest request, String currentUsername);

    /** Voids a cash transfer of the open cash session (POS screen). */
    FinancialTransactionResponse voidCashTransfer(UUID id, VoidFinancialTransactionRequest request, String currentUsername);
}
