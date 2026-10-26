package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.ConsumptionsReportResponse;
import com.akhana.akhana_admin.dto.ReportFile;
import com.akhana.akhana_admin.dto.SalesByDayReportResponse;
import com.akhana.akhana_admin.dto.SalesBySupplierReportResponse;
import com.akhana.akhana_admin.dto.TotalsReportResponse;
import com.akhana.akhana_admin.dto.TransactionsByCategoryReportResponse;
import com.akhana.akhana_admin.service.ReportExportService;
import com.akhana.akhana_admin.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@PreAuthorize("hasAuthority('REPORTS')")
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    @GetMapping("/sales-by-day")
    public ResponseEntity<SalesByDayReportResponse> getSalesByDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getSalesByDay(from, to));
    }

    @GetMapping("/sales-by-supplier")
    public ResponseEntity<SalesBySupplierReportResponse> getSalesBySupplier(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getSalesBySupplier(from, to));
    }

    @GetMapping("/transactions-by-category")
    public ResponseEntity<TransactionsByCategoryReportResponse> getTransactionsByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getTransactionsByCategory(from, to));
    }

    @GetMapping("/totals")
    public ResponseEntity<TotalsReportResponse> getTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getTotals(from, to));
    }

    @GetMapping("/consumptions")
    public ResponseEntity<ConsumptionsReportResponse> getConsumptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getConsumptions(from, to));
    }

    @GetMapping("/sales-by-day/export")
    public ResponseEntity<byte[]> exportSalesByDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return file(reportExportService.exportSalesByDay(from, to));
    }

    @GetMapping("/sales-by-supplier/export")
    public ResponseEntity<byte[]> exportSalesBySupplier(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return file(reportExportService.exportSalesBySupplier(from, to));
    }

    @GetMapping("/transactions-by-category/export")
    public ResponseEntity<byte[]> exportTransactionsByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return file(reportExportService.exportTransactionsByCategory(from, to));
    }

    @GetMapping("/totals/export")
    public ResponseEntity<byte[]> exportTotals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return file(reportExportService.exportTotals(from, to));
    }

    @GetMapping("/consumptions/export")
    public ResponseEntity<byte[]> exportConsumptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return file(reportExportService.exportConsumptions(from, to));
    }

    // FIN-10: the same report as an .xlsx download named akhana-<report>-<from>-<to>.xlsx
    private static ResponseEntity<byte[]> file(ReportFile report) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .body(report.content());
    }
}
