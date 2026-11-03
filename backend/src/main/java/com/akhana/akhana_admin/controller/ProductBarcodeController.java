package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.BarcodePendingResponse;
import com.akhana.akhana_admin.dto.ProductBarcodeRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.ProductBarcodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// CAT-10, POS-15
@RestController
@PreAuthorize("hasAuthority('PRODUCTS')")
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductBarcodeController {

    private final ProductBarcodeService barcodeService;

    // POS-15: a code that is not in the catalog the POS loaded; POS can already read a product by id
    @PreAuthorize("hasAuthority('POS')")
    @GetMapping("/by-barcode/{barcode}")
    public ResponseEntity<ProductResponse> findByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(barcodeService.findByBarcode(barcode));
    }

    @GetMapping("/barcode-pending")
    public ResponseEntity<List<BarcodePendingResponse>> findPending() {
        return ResponseEntity.ok(barcodeService.findPending());
    }

    @PostMapping("/{id}/barcodes")
    public ResponseEntity<ProductResponse> addBarcode(
            @PathVariable UUID id,
            @Valid @RequestBody ProductBarcodeRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(barcodeService.addBarcode(id, request.barcode(), username(authentication)));
    }

    @PutMapping("/{id}/no-barcode")
    public ResponseEntity<ProductResponse> markNoBarcode(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(barcodeService.markNoBarcode(id, username(authentication)));
    }

    @DeleteMapping("/{id}/no-barcode")
    public ResponseEntity<ProductResponse> clearNoBarcode(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(barcodeService.clearNoBarcode(id, username(authentication)));
    }

    // The principal is our User, whose toString is not its name
    private static String username(Authentication authentication) {
        return authentication.getPrincipal() instanceof User user ? user.getUsername() : authentication.getName();
    }
}
