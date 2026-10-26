package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Active consumptions at cost (CON-03): per store day with month subtotals and the range total, and per supplier
 * and month. Voided consumptions never count. Both blocks add up to the same total at cost.
 */
public record ConsumptionsReportResponse(LocalDate from,
                                         LocalDate to,
                                         List<Month> months,
                                         DayRow totals,
                                         List<Supplier> suppliers,
                                         SupplierRow supplierTotals) {

    /** A month ("2026-07") with its days and their sum. */
    public record Month(String month, List<DayRow> days, DayRow totals) {
    }

    /** One day, or the sum of several when {@code date} is null; quantity is the sum of the lines (kilos too). */
    public record DayRow(LocalDate date, long consumptions, BigDecimal quantity, BigDecimal total) {
    }

    /** A supplier with its months and their sum; {@code supplierId} is null for lines without one. */
    public record Supplier(UUID supplierId, String supplierName, List<SupplierRow> months, SupplierRow totals) {
    }

    /** One month ("2026-07"), or the sum of several when {@code month} is null. */
    public record SupplierRow(String month, BigDecimal quantity, BigDecimal total) {
    }
}
