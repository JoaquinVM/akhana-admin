package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.ProductRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.ProductService;
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
@PreAuthorize("hasAuthority('PRODUCTS')")
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // SALES_HISTORY picks the real product of an unidentified line (POS-12)
    @PreAuthorize("hasAnyAuthority('PRODUCTS', 'POS', 'PURCHASES', 'CONSUMPTIONS', 'SALES_HISTORY')")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        List<ProductResponse> products = productService.getAllProducts(search, status);
        return ResponseEntity.ok(products);
    }

    @PreAuthorize("hasAnyAuthority('PRODUCTS', 'POS', 'PURCHASES', 'CONSUMPTIONS')")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    // Compras creates a product from a purchase line (COM-03)
    @PreAuthorize("hasAnyAuthority('PRODUCTS', 'PURCHASES')")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        ProductResponse created = productService.createProduct(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        ProductResponse updated = productService.updateProduct(id, request, username);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> changeProductStatus(
            @PathVariable UUID id,
            @RequestParam ProductStatus status,
            Authentication authentication) {
        String username = extractUsername(authentication);
        ProductResponse updated = productService.changeProductStatus(id, status, username);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable UUID id,
            Authentication authentication) {
        String username = extractUsername(authentication);
        productService.deleteProduct(id, username);
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
