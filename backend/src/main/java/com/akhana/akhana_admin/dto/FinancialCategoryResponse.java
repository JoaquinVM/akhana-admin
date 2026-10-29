package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.FinancialCategorySystemKey;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCategoryResponse {
    private UUID id;
    private String name;
    private FinancialCategoryType type;
    private String color;
    private Boolean isSalesDefault;
    private FinancialCategorySystemKey systemKey;
    // False for the categories a manual movement cannot use (Pedido, Pago a tercero, Ajuste, ventas)
    private boolean manualEntryAllowed;

    // Audit
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String deletedBy;
    private Instant deletedAt;
}
