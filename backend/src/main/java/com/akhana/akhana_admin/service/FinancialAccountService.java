package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialAccountRequest;
import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.OpeningBalanceRequest;

import java.util.List;
import java.util.UUID;

public interface FinancialAccountService {

    List<FinancialAccountResponse> getAllAccounts();

    FinancialAccountResponse getAccountById(UUID id);

    FinancialAccountResponse createAccount(FinancialAccountRequest request, String currentUsername);

    FinancialAccountResponse updateAccount(UUID id, FinancialAccountRequest request, String currentUsername);

    FinancialAccountResponse setQrAccount(UUID id, String currentUsername);

    FinancialAccountResponse setOpeningBalance(UUID id, OpeningBalanceRequest request, String currentUsername);

    void deleteAccount(UUID id, String currentUsername);
}
