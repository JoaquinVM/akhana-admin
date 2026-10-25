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
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialTransactionResponse {
    private UUID id;
    private FinancialTransactionType type;
    private BigDecimal amount;
    private Instant transactionDate;
    private String description;
    private String notes;
    private FinancialTransactionStatus status;
    private UUID saleId;
    private UUID cashSessionId;
    private UUID purchaseId;
    private UUID creditorRepaymentId;

    private AccountSummary sourceAccount;
    private AccountSummary destinationAccount;
    private CategorySummary category;

    // Auditoría de registro
    private String createdBy;
    private Instant createdAt;

    // Auditoría de modificación
    private String updatedBy;
    private Instant updatedAt;

    // Auditoría de anulación
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
