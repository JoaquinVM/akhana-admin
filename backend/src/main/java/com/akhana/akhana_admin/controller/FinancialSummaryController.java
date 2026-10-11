package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.FinancialSummaryResponse;
import com.akhana.akhana_admin.service.FinancialSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finances/summary")
@RequiredArgsConstructor
public class FinancialSummaryController {

    private final FinancialSummaryService financialSummaryService;

    @GetMapping
    public ResponseEntity<FinancialSummaryResponse> getSummary() {
        return ResponseEntity.ok(financialSummaryService.getSummary());
    }
}
