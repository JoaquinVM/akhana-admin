package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.CustomerStatus;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.CustomerService;
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
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // The POS lists the active customers for an at-cost sale on credit (DEU-05)
    @PreAuthorize("hasAnyAuthority('DEBTS', 'POS')")
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getCustomers(
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(customerService.getCustomers(status, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CustomerRequest request,
            Authentication authentication) {
        CustomerResponse created = customerService.createCustomer(request, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request, extractUsername(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id, Authentication authentication) {
        customerService.deleteCustomer(id, extractUsername(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<CustomerHistoryResponse> getHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getHistory(id));
    }

    @PostMapping("/{id}/collections")
    public ResponseEntity<CustomerHistoryResponse> registerCollection(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerCollectionRequest request,
            Authentication authentication) {
        CustomerHistoryResponse updated = customerService.registerCollection(id, request, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @PostMapping("/{id}/collections/{collectionId}/void")
    public ResponseEntity<CustomerHistoryResponse> voidCollection(
            @PathVariable UUID id,
            @PathVariable UUID collectionId,
            @Valid @RequestBody VoidDebtRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(customerService.voidCollection(id, collectionId, request, extractUsername(authentication)));
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
