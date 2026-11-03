package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.ProductWebPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// CAT-13: the Products screen permission (P-33)
@RestController
@PreAuthorize("hasAuthority('PRODUCTS')")
@RequestMapping("/api/products/{id}/web-publication")
@RequiredArgsConstructor
public class ProductWebPublicationController {

    private final ProductWebPublicationService publicationService;

    @PutMapping
    public ResponseEntity<ProductResponse> mark(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(publicationService.mark(id, username(authentication)));
    }

    @DeleteMapping
    public ResponseEntity<ProductResponse> unmark(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(publicationService.unmark(id, username(authentication)));
    }

    private String username(Authentication authentication) {
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
