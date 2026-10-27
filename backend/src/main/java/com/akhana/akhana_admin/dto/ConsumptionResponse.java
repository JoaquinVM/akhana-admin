package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.Consumption;
import com.akhana.akhana_admin.model.ConsumptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Consumption valued at cost (CON-01). The list leaves items empty; the detail fills them. */
public record ConsumptionResponse(
    UUID id,
    String consumptionNumber,
    LocalDate consumptionDate,
    String consumedFor,
    String comment,
    BigDecimal totalAmount,
    ConsumptionStatus status,
    String createdBy,
    Instant createdAt,
    String voidedBy,
    Instant voidedAt,
    String voidReason,
    List<ConsumptionItemResponse> items,
    // FIN-11: migrated from the spreadsheet
    boolean historical
) {
    public static ConsumptionResponse summary(Consumption consumption) {
        return of(consumption, List.of());
    }

    public static ConsumptionResponse detail(Consumption consumption) {
        return of(consumption, consumption.getItems().stream().map(ConsumptionItemResponse::fromEntity).toList());
    }

    private static ConsumptionResponse of(Consumption consumption, List<ConsumptionItemResponse> items) {
        return new ConsumptionResponse(
            consumption.getId(),
            consumption.getConsumptionNumber(),
            consumption.getConsumptionDate(),
            consumption.getConsumedFor(),
            consumption.getComment(),
            consumption.getTotalAmount(),
            consumption.getStatus(),
            consumption.getCreatedBy(),
            consumption.getCreatedAt(),
            consumption.getVoidedBy(),
            consumption.getVoidedAt(),
            consumption.getVoidReason(),
            items,
            consumption.isHistorical()
        );
    }
}
