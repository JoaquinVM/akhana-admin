package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ReportFile;

import java.time.LocalDate;

/**
 * Excel export of the reports (FIN-10): the same range, columns and rows as the screen, built from the same
 * {@link ReportService} responses so the file always shows the screen's numbers.
 */
public interface ReportExportService {
    ReportFile exportSalesByDay(LocalDate from, LocalDate to);

    ReportFile exportSalesBySupplier(LocalDate from, LocalDate to);

    ReportFile exportTransactionsByCategory(LocalDate from, LocalDate to);

    ReportFile exportTotals(LocalDate from, LocalDate to);

    ReportFile exportConsumptions(LocalDate from, LocalDate to);
}
