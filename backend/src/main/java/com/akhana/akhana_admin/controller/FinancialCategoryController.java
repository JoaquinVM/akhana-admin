package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.FinancialCategoryRequest;
import com.akhana.akhana_admin.dto.FinancialCategoryResponse;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.FinancialCategoryService;
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
@PreAuthorize("hasAuthority('FINANCES_CATEGORIES')")
@RequestMapping("/api/finances/categories")
@RequiredArgsConstructor
public class FinancialCategoryController {

    private final FinancialCategoryService financialCategoryService;

    @PreAuthorize("hasAnyAuthority('FINANCES_CATEGORIES', 'FINANCES_TRANSACTIONS', 'POS')")
    @GetMapping
    public ResponseEntity<List<FinancialCategoryResponse>> getAllCategories(
            @RequestParam(required = false) FinancialCategoryType type
    ) {
        return ResponseEntity.ok(financialCategoryService.getAllCategories(type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FinancialCategoryResponse> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(financialCategoryService.getCategoryById(id));
    }

    @PostMapping
    public ResponseEntity<FinancialCategoryResponse> createCategory(
            @Valid @RequestBody FinancialCategoryRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        FinancialCategoryResponse created = financialCategoryService.createCategory(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinancialCategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody FinancialCategoryRequest request,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialCategoryService.updateCategory(id, request, username));
    }

    @PutMapping("/{id}/sales-default")
    public ResponseEntity<FinancialCategoryResponse> setSalesDefaultCategory(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        return ResponseEntity.ok(financialCategoryService.setSalesDefaultCategory(id, username));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String username = extractUsername(authentication);
        financialCategoryService.deleteCategory(id, username);
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
