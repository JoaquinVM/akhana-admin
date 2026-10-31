package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.FinancialCategoryType;
import com.akhana.akhana_admin.model.FinancialTransactionStatus;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialTransactionResponse {
    private UUID id;
    private FinancialTransactionType type;
    private BigDecimal amount;
    // Real date and time it was recorded
    private Instant transactionDate;
    // CAJ-11: day it counts for (its session's or sale's day, else the day it was recorded)
    private LocalDate operationDate;
    private String description;
    private String notes;
    private FinancialTransactionStatus status;
    private UUID saleId;
    private UUID cashSessionId;
    private UUID purchaseId;
    private UUID creditorRepaymentId;
    private UUID customerCollectionId;
    // FIN-11: migrated from Transacc.; it does not count in the balances and cannot be edited or voided
    private boolean historical;

    private AccountSummary sourceAccount;
    private AccountSummary destinationAccount;
    private CategorySummary category;

    // Creation audit
    private String createdBy;
    private Instant createdAt;

    // Update audit
    private String updatedBy;
    private Instant updatedAt;

    // Void audit
    private String voidedBy;
    private Instant voidedAt;
    private String voidReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountSummary {
        private UUID id;
        private String name;
        private String color;
        // Lets the POS tell a cash income from a withdrawal (CAJ-08)
        private boolean cashAccount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private UUID id;
        private String name;
        private FinancialCategoryType type;
        private String color;
    }
}
