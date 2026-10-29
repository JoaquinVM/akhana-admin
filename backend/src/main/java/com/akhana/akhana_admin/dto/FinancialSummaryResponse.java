package com.akhana.akhana_admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSummaryResponse {
    private BigDecimal cashBalance;
    private BigDecimal totalBalance;
    private UUID qrAccountId;
    private String qrAccountName;
    private List<FinancialAccountResponse> accounts;
    private List<FinancialTransactionResponse> recentTransactions;
}
