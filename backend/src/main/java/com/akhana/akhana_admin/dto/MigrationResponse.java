package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * FIN-11: result of reviewing or importing the historical migration. Rejected rows block the import; notices do
 * not. The month totals are for comparing with the pivot tables of the spreadsheet.
 */
public record MigrationResponse(
    LocalDate cutoffDate,
    boolean imported,
    List<SheetCount> sheets,
    List<RowMessage> rejected,
    List<RowMessage> notices,
    int unassignedLines,
    BigDecimal unassignedTotal,
    List<OwnerDebt> thirdPartyDebts,
    List<MonthTotals> months,
    List<MovementTotal> movements
) {
    /** Rows read, to import, rejected and dated after the cutoff of a sheet. */
    public record SheetCount(String sheet, int read, int toImport, int rejected, int afterCutoff) {}

    /** rowNumber is the spreadsheet row (the title is row 1). */
    public record RowMessage(String sheet, int rowNumber, List<String> messages) {}

    /** Initial debt with the owner of third-party products (POS-13): the sum of their historical sales. */
    public record OwnerDebt(String creditorName, BigDecimal amount) {}

    /**
     * Per month: normal sales (including the unassigned lines, without third-party ones), profit of the normal
     * lines with a known cost, unassigned, at-cost and third-party sales, and consumptions at cost.
     */
    public record MonthTotals(String month, BigDecimal sales, BigDecimal profit, BigDecimal unassigned,
                              BigDecimal atCost, BigDecimal thirdParty, BigDecimal consumptions) {}

    /** Net per month and category (income +, expense −) or per pair of accounts for transfers. */
    public record MovementTotal(String month, String concept, BigDecimal amount) {}
}
