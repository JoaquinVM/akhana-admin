package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SalesByDayReportResponse;

import java.time.LocalDate;

public interface ReportService {
    /** Sales per store day between {@code from} and {@code to} (both included); missing dates mean this month. */
    SalesByDayReportResponse getSalesByDay(LocalDate from, LocalDate to);
}
