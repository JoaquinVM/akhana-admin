package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.PurchasePaymentStatus;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('PURCHASES')")
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> getPurchases(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchasePaymentStatus paymentStatus,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(purchaseService.getPurchases(supplierId, paymentStatus, search));
    }

    @GetMapping("/payment-accounts")
    public ResponseEntity<List<PaymentAccountResponse>> getPaymentAccounts() {
        return ResponseEntity.ok(purchaseService.getPaymentAccounts());
    }

    @GetMapping("/price-suggestion")
    public ResponseEntity<PriceSuggestionResponse> suggestPrice(
            @RequestParam UUID productId,
            @RequestParam BigDecimal newCost) {
        return ResponseEntity.ok(purchaseService.suggestPrice(productId, newCost));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseResponse> getPurchaseById(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.getPurchaseById(id));
    }

    @PostMapping
    public ResponseEntity<PurchaseResponse> registerPurchase(
            @Valid @RequestBody PurchaseRequest request,
            Authentication authentication) {
        PurchaseResponse created = purchaseService.registerPurchase(request, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<PurchaseResponse> registerPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PurchasePaymentRequest request,
            Authentication authentication) {
        PurchaseResponse updated = purchaseService.registerPayment(id, request, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
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
