package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.ProductImportResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.ProductImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// CAT-06: the Products screen permission (P-33), no new one
@RestController
@PreAuthorize("hasAuthority('PRODUCTS')")
@RequestMapping("/api/products/import")
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService productImportService;

    @PostMapping("/preview")
    public ResponseEntity<ProductImportResponse> preview(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(productImportService.preview(file.getOriginalFilename(), file.getBytes()));
    }

    @PostMapping
    public ResponseEntity<ProductImportResponse> importProducts(@RequestParam("file") MultipartFile file,
                                                                Authentication authentication) throws IOException {
        return ResponseEntity.ok(productImportService.importProducts(file.getOriginalFilename(), file.getBytes(),
            extractUsername(authentication)));
    }

    private String extractUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
