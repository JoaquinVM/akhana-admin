package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.service.FinancialSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('FINANCES_SUMMARY')")
@RequestMapping("/api/finances/summary")
@RequiredArgsConstructor
public class FinancialSummaryController {

    private final FinancialSummaryService financialSummaryService;

    @GetMapping
    public ResponseEntity<FinancialSummaryResponse> getSummary() {
        return ResponseEntity.ok(financialSummaryService.getSummary());
    }
}
