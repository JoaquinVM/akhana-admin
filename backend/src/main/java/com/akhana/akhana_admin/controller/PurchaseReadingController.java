package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.PurchaseDraftResponse;
import com.akhana.akhana_admin.dto.PurchaseReadingStatusResponse;
import com.akhana.akhana_admin.dto.PurchaseTextReadingRequest;
import com.akhana.akhana_admin.model.Role;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.PurchaseReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// COM-20: the Compras permission (P-33). A reading only answers a draft; it registers nothing
@RestController
@PreAuthorize("hasAuthority('PURCHASES')")
@RequestMapping("/api/purchases/readings")
@RequiredArgsConstructor
public class PurchaseReadingController {

    private final PurchaseReadingService readingService;

    @GetMapping("/status")
    public ResponseEntity<PurchaseReadingStatusResponse> getStatus(Authentication authentication) {
        boolean administrator = authentication.getPrincipal() instanceof User user && user.getRole() == Role.ADMINISTRATOR;
        return ResponseEntity.ok(readingService.getStatus(administrator));
    }

    @PostMapping("/text")
    public ResponseEntity<PurchaseDraftResponse> readText(@Valid @RequestBody PurchaseTextReadingRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(readingService.readText(request.text(), request.supplierId(), extractUsername(authentication)));
    }

    @PostMapping("/photo")
    public ResponseEntity<PurchaseDraftResponse> readPhoto(@RequestParam("file") MultipartFile file,
                                                           Authentication authentication) throws IOException {
        return ResponseEntity.ok(readingService.readPhoto(file.getBytes(), extractUsername(authentication)));
    }

    private String extractUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
