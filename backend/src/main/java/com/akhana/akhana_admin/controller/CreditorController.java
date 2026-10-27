package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.CreditorStatus;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.CreditorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('DEBTS')")
@RequestMapping("/api/creditors")
@RequiredArgsConstructor
public class CreditorController {

    private final CreditorService creditorService;

    // Compras lists the active people for a payment made by a person (DEU-02); Productos for the owner of a
    // third-party product (POS-13)
    @PreAuthorize("hasAnyAuthority('DEBTS', 'PURCHASES', 'PRODUCTS')")
    @GetMapping
    public ResponseEntity<List<CreditorResponse>> getCreditors(
            @RequestParam(required = false) CreditorStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(creditorService.getCreditors(status, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditorResponse> getCreditorById(@PathVariable UUID id) {
        return ResponseEntity.ok(creditorService.getCreditorById(id));
    }

    @PostMapping
    public ResponseEntity<CreditorResponse> createCreditor(
            @Valid @RequestBody CreditorRequest request,
            Authentication authentication) {
        CreditorResponse created = creditorService.createCreditor(request, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CreditorResponse> updateCreditor(
            @PathVariable UUID id,
            @Valid @RequestBody CreditorRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(creditorService.updateCreditor(id, request, extractUsername(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCreditor(@PathVariable UUID id, Authentication authentication) {
        creditorService.deleteCreditor(id, extractUsername(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<CreditorHistoryResponse> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(creditorService.getHistory(id));
    }

    @PostMapping("/{id}/repayments")
    public ResponseEntity<CreditorHistoryResponse> registerRepayment(
            @PathVariable UUID id,
            @Valid @RequestBody CreditorRepaymentRequest request,
            Authentication authentication) {
        CreditorHistoryResponse updated = creditorService.registerRepayment(id, request, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @PostMapping("/{id}/repayments/{repaymentId}/void")
    public ResponseEntity<CreditorHistoryResponse> voidRepayment(
            @PathVariable UUID id,
            @PathVariable UUID repaymentId,
            @Valid @RequestBody VoidDebtRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(creditorService.voidRepayment(id, repaymentId, request, extractUsername(authentication)));
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
