package com.akhana.akhana_admin.dto;

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

    // Auditoría
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String deletedBy;
    private Instant deletedAt;
}
