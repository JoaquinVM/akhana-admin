package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.CashSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cash-sessions")
@RequiredArgsConstructor
public class CashSessionController {

    private final CashSessionService cashSessionService;

    @PostMapping("/open")
    public ResponseEntity<CashSessionSummaryResponse> openSession(
            @Valid @RequestBody OpenCashRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        CashSessionSummaryResponse response = cashSessionService.openSession(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/current")
    public ResponseEntity<CashSessionSummaryResponse> getCurrentSession() {
        CashSessionSummaryResponse response = cashSessionService.getCurrentSession();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/current/sales")
    public ResponseEntity<SaleResponse> registerSale(
            @Valid @RequestBody SaleRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        SaleResponse response = cashSessionService.registerSaleInCurrentSession(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/current/close")
    public ResponseEntity<CashSessionSummaryResponse> closeSession(
            @Valid @RequestBody CloseCashRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        CashSessionSummaryResponse response = cashSessionService.closeCurrentSession(request, username);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CashSessionSummaryResponse>> getAllSessions() {
        List<CashSessionSummaryResponse> sessions = cashSessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CashSessionDetailResponse> getSessionDetail(@PathVariable UUID id) {
        CashSessionDetailResponse response = cashSessionService.getSessionDetail(id);
        return ResponseEntity.ok(response);
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null) {
            return "admin";
        }
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
