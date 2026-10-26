package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.CashTransferRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionRequest;
import com.akhana.akhana_admin.dto.FinancialTransactionResponse;
import com.akhana.akhana_admin.dto.PaymentAccountResponse;
import com.akhana.akhana_admin.dto.VoidFinancialTransactionRequest;
import com.akhana.akhana_admin.model.FinancialTransactionStatus;
import com.akhana.akhana_admin.model.FinancialTransactionType;
import com.akhana.akhana_admin.model.User;
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
@PreAuthorize("hasAuthority('FINANCES_TRANSACTIONS')")
@RequestMapping("/api/finances/transactions")
@RequiredArgsConstructor
public class FinancialTransactionController {

    private final FinancialTransactionService financialTransactionService;
    private final com.akhana.akhana_admin.repository.FinancialAccountRepository financialAccountRepository;

    @PreAuthorize("hasAnyAuthority('FINANCES_TRANSACTIONS', 'FINANCES_ACCOUNTS')")
    @GetMapping
    public ResponseEntity<List<FinancialTransactionResponse>> getTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) FinancialTransactionType type,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) FinancialTransactionStatus status,
            @RequestParam(required = false) String search
    ) {
        List<FinancialTransactionResponse> transactions = financialTransactionService.getTransactions(
                startDate, endDate, type, accountId, categoryId, status, search
        );
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FinancialTransactionResponse> getTransactionById(@PathVariable UUID id) {
        return ResponseEntity.ok(financialTransactionService.getTransactionById(id));
    }

    @PostMapping
    public ResponseEntity<FinancialTransactionResponse> createTransaction(
            @Valid @RequestBody FinancialTransactionRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        FinancialTransactionResponse created = financialTransactionService.createTransaction(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasAuthority('POS')")
    @PostMapping("/cash-expense")
    public ResponseEntity<FinancialTransactionResponse> createCashExpense(
            @Valid @RequestBody com.akhana.akhana_admin.dto.CashExpenseRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        com.akhana.akhana_admin.model.FinancialAccount cashAccount = financialAccountRepository.findByIsCashAccountTrue()
                .orElseThrow(() -> new com.akhana.akhana_admin.exception.ResourceNotFoundException("No se encontró la cuenta Caja en el sistema."));

        FinancialTransactionRequest txRequest = FinancialTransactionRequest.builder()
                .type(FinancialTransactionType.GASTO_MANUAL)
                .sourceAccountId(cashAccount.getId())
                .categoryId(request.getCategoryId())
                .amount(request.getAmount())
                .description(request.getDescription())
                .notes(request.getNotes())
                .build();

        FinancialTransactionResponse created = financialTransactionService.createTransaction(txRequest, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasAuthority('POS')")
    @GetMapping("/cash-transfer/accounts")
    public ResponseEntity<List<PaymentAccountResponse>> getCashTransferAccounts() {
        return ResponseEntity.ok(financialTransactionService.getCashTransferAccounts());
    }

    @PreAuthorize("hasAuthority('POS')")
    @PostMapping("/cash-transfer")
    public ResponseEntity<FinancialTransactionResponse> createCashTransfer(
            @Valid @RequestBody CashTransferRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        FinancialTransactionResponse created = financialTransactionService.createCashTransfer(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasAuthority('POS')")
    @PostMapping("/cash-transfer/{id}/void")
    public ResponseEntity<FinancialTransactionResponse> voidCashTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) VoidFinancialTransactionRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialTransactionService.voidCashTransfer(id, request, username));
    }

    @PreAuthorize("hasAnyAuthority('POS', 'CASH_HISTORY')")
    @GetMapping("/session/{cashSessionId}")
    public ResponseEntity<List<FinancialTransactionResponse>> getExpensesByCashSession(@PathVariable UUID cashSessionId) {
        return ResponseEntity.ok(financialTransactionService.getExpensesByCashSession(cashSessionId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinancialTransactionResponse> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody FinancialTransactionRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialTransactionService.updateTransaction(id, request, username));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<FinancialTransactionResponse> voidTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) VoidFinancialTransactionRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialTransactionService.voidTransaction(id, request, username));
    }

    @PreAuthorize("hasAuthority('POS')")
    @PostMapping("/cash-expense/{id}/void")
    public ResponseEntity<FinancialTransactionResponse> voidCashExpense(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) VoidFinancialTransactionRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialTransactionService.voidCashExpense(id, request, username));
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
