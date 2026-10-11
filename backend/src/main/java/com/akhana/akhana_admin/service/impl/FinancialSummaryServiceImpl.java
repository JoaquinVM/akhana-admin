package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.model.FinancialAccount;
import com.akhana.akhana_admin.model.FinancialTransaction;
import com.akhana.akhana_admin.repository.FinancialAccountRepository;
import com.akhana.akhana_admin.repository.FinancialTransactionRepository;
import com.akhana.akhana_admin.service.FinancialSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialSummaryServiceImpl implements FinancialSummaryService {

    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public FinancialSummaryResponse getSummary() {
        List<FinancialAccount> accounts = financialAccountRepository.findByDeletedAtIsNullOrderByNameAsc();
        List<FinancialTransaction> recentTransactions = financialTransactionRepository.findTop10ByOrderByTransactionDateDesc();

        BigDecimal cashBalance = BigDecimal.ZERO;
        BigDecimal bisaBalance = BigDecimal.ZERO;
        BigDecimal joacoBalance = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;

        UUID qrAccountId = null;
        String qrAccountName = null;

        for (FinancialAccount acc : accounts) {
            BigDecimal bal = acc.getCurrentBalance() != null ? acc.getCurrentBalance() : BigDecimal.ZERO;
            totalBalance = totalBalance.add(bal);

            if (Boolean.TRUE.equals(acc.getIsCashAccount())) {
                cashBalance = bal;
            }
            if (acc.getName() != null) {
                String lower = acc.getName().toLowerCase();
                if (lower.contains("bisa")) {
                    bisaBalance = bal;
                } else if (lower.contains("joaco")) {
                    joacoBalance = bal;
                }
            }
            if (Boolean.TRUE.equals(acc.getIsQrAccount())) {
                qrAccountId = acc.getId();
                qrAccountName = acc.getName();
            }
        }

        List<FinancialAccountResponse> accountResponses = accounts.stream()
                .map(this::mapAccountToResponse)
                .collect(Collectors.toList());

        List<FinancialTransactionResponse> transactionResponses = recentTransactions.stream()
                .map(this::mapTransactionToResponse)
                .collect(Collectors.toList());

        return FinancialSummaryResponse.builder()
                .cashBalance(cashBalance)
                .bisaBalance(bisaBalance)
                .joacoBalance(joacoBalance)
                .totalBalance(totalBalance)
                .qrAccountId(qrAccountId)
                .qrAccountName(qrAccountName)
                .accounts(accountResponses)
                .recentTransactions(transactionResponses)
                .build();
    }

    private FinancialAccountResponse mapAccountToResponse(FinancialAccount account) {
        return FinancialAccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .color(account.getColor())
                .initialBalance(account.getInitialBalance())
                .currentBalance(account.getCurrentBalance())
                .isCashAccount(account.getIsCashAccount())
                .isQrAccount(account.getIsQrAccount())
                .createdBy(account.getCreatedBy())
                .createdAt(account.getCreatedAt())
                .updatedBy(account.getUpdatedBy())
                .updatedAt(account.getUpdatedAt())
                .deletedBy(account.getDeletedBy())
                .deletedAt(account.getDeletedAt())
                .build();
    }

    private FinancialTransactionResponse mapTransactionToResponse(FinancialTransaction tx) {
        FinancialTransactionResponse.AccountSummary sourceSummary = null;
        if (tx.getSourceAccount() != null) {
            sourceSummary = FinancialTransactionResponse.AccountSummary.builder()
                    .id(tx.getSourceAccount().getId())
                    .name(tx.getSourceAccount().getName())
                    .color(tx.getSourceAccount().getColor())
                    .build();
        }

        FinancialTransactionResponse.AccountSummary destSummary = null;
        if (tx.getDestinationAccount() != null) {
            destSummary = FinancialTransactionResponse.AccountSummary.builder()
                    .id(tx.getDestinationAccount().getId())
                    .name(tx.getDestinationAccount().getName())
                    .color(tx.getDestinationAccount().getColor())
                    .build();
        }

        FinancialTransactionResponse.CategorySummary catSummary = null;
        if (tx.getCategory() != null) {
            catSummary = FinancialTransactionResponse.CategorySummary.builder()
                    .id(tx.getCategory().getId())
                    .name(tx.getCategory().getName())
                    .type(tx.getCategory().getType())
                    .build();
        }

        return FinancialTransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .transactionDate(tx.getTransactionDate())
                .description(tx.getDescription())
                .notes(tx.getNotes())
                .status(tx.getStatus())
                .saleId(tx.getSaleId())
                .sourceAccount(sourceSummary)
                .destinationAccount(destSummary)
                .category(catSummary)
                .createdBy(tx.getCreatedBy())
                .createdAt(tx.getCreatedAt())
                .updatedBy(tx.getUpdatedBy())
                .updatedAt(tx.getUpdatedAt())
                .voidedBy(tx.getVoidedBy())
                .voidedAt(tx.getVoidedAt())
                .voidReason(tx.getVoidReason())
                .build();
    }
}
