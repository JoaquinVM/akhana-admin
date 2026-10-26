package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesBySupplierReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse;

import java.time.LocalDate;

public interface ReportService {
    /** Sales per store day between {@code from} and {@code to} (both included); missing dates mean this month. */
    SalesByDayReportResponse getSalesByDay(LocalDate from, LocalDate to);

    /** Sales per supplier and store month, with the general discount spread over the lines (FIN-10, CON-03). */
    SalesBySupplierReportResponse getSalesBySupplier(LocalDate from, LocalDate to);

    /** Movements per store month and category, with transfers and consumptions at cost apart (FIN-10, CON-03). */
    TransactionsByCategoryReportResponse getTransactionsByCategory(LocalDate from, LocalDate to);
}
