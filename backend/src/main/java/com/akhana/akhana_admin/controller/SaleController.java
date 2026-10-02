package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<SaleResponse> registerSale(
            @Valid @RequestBody SaleDetailRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        SaleResponse response = saleService.registerSale(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<SaleResponse> voidSale(
            @PathVariable UUID id,
            @Valid @RequestBody VoidSaleRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        SaleResponse response = saleService.voidSale(id, request, username);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SaleResponse>> getAllSales() {
        return ResponseEntity.ok(saleService.getAllSales());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleResponse> getSaleById(@PathVariable UUID id) {
        return ResponseEntity.ok(saleService.getSaleById(id));
    }

    @GetMapping("/session/{cashSessionId}")
    public ResponseEntity<List<SaleResponse>> getSalesByCashSession(@PathVariable UUID cashSessionId) {
        return ResponseEntity.ok(saleService.getSalesByCashSession(cashSessionId));
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null) {
            return "SYSTEM";
        }
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
