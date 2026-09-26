package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.SupplierRequest;
import com.akhana.akhana_admin.dto.SupplierResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> getAllSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        List<SupplierResponse> suppliers = supplierService.getAllSuppliers(search, status);
        return ResponseEntity.ok(suppliers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(@PathVariable UUID id) {
        SupplierResponse supplier = supplierService.getSupplierById(id);
        return ResponseEntity.ok(supplier);
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(
            @Valid @RequestBody SupplierRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        SupplierResponse created = supplierService.createSupplier(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        SupplierResponse updated = supplierService.updateSupplier(id, request, username);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(
            @PathVariable UUID id,
            Authentication authentication) {
        String username = extractUsername(authentication);
        supplierService.deleteSupplier(id, username);
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
