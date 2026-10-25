package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.FinancialAccountRequest;
import com.akhana.akhana_admin.dto.FinancialAccountResponse;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.FinancialAccountService;
import com.akhana.akhana_admin.service.FinancialTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('FINANCES_ACCOUNTS')")
@RequestMapping("/api/finances/accounts")
@RequiredArgsConstructor
public class FinancialAccountController {

    private final FinancialAccountService financialAccountService;
    private final FinancialTransactionService financialTransactionService;

    @PreAuthorize("hasAnyAuthority('FINANCES_ACCOUNTS', 'FINANCES_TRANSACTIONS')")
    @GetMapping
    public ResponseEntity<List<FinancialAccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(financialAccountService.getAllAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FinancialAccountResponse> getAccountById(@PathVariable UUID id) {
        return ResponseEntity.ok(financialAccountService.getAccountById(id));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<FinancialTransactionResponse>> getAccountTransactions(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        return ResponseEntity.ok(financialTransactionService.getAccountTransactions(id, startDate, endDate));
    }

    @PostMapping
    public ResponseEntity<FinancialAccountResponse> createAccount(
            @Valid @RequestBody FinancialAccountRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        FinancialAccountResponse created = financialAccountService.createAccount(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinancialAccountResponse> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody FinancialAccountRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialAccountService.updateAccount(id, request, username));
    }

    @PutMapping("/{id}/qr")
    public ResponseEntity<FinancialAccountResponse> setQrAccount(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialAccountService.setQrAccount(id, username));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        financialAccountService.deleteAccount(id, username);
        return ResponseEntity.noContent().build();
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
