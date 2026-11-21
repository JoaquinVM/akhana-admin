package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.FinancialAccountService;
import com.akhana.akhana_admin.service.FinancialSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialSummaryServiceImpl implements FinancialSummaryService {

    private final FinancialAccountService financialAccountService;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public FinancialSummaryResponse getSummary() {
        // The same accounts and movements, field by field, as the accounts and movements screens
        List<FinancialAccountResponse> accounts = financialAccountService.getAllAccounts();
        List<FinancialTransactionResponse> recentTransactions = financialTransactionRepository
                .findTop10ByOrderByTransactionDateDesc().stream()
                .map(FinancialTransactionServiceImpl::mapToResponse)
                .toList();

        BigDecimal cashBalance = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;

        UUID qrAccountId = null;
        String qrAccountName = null;

        for (FinancialAccountResponse acc : accounts) {
            BigDecimal bal = acc.getCurrentBalance() != null ? acc.getCurrentBalance() : BigDecimal.ZERO;
            totalBalance = totalBalance.add(bal);

            if (Boolean.TRUE.equals(acc.getIsCashAccount())) {
                cashBalance = bal;
            }
            if (Boolean.TRUE.equals(acc.getIsQrAccount())) {
                qrAccountId = acc.getId();
                qrAccountName = acc.getName();
            }
        }

        return FinancialSummaryResponse.builder()
                .cashBalance(cashBalance)
                .totalBalance(totalBalance)
                .qrAccountId(qrAccountId)
                .qrAccountName(qrAccountName)
                .accounts(accounts)
                .recentTransactions(recentTransactions)
                .build();
    }
}
