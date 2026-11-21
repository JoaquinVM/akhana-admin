package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.BoxConversionRequest;
import com.akhana.akhana_admin.dto.BoxProposalResponse;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.BoxProductConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// COM-10: boxes sold as their own product become packages of their base product (CAT-15)
@RestController
@PreAuthorize("hasAuthority('PRODUCTS')")
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class BoxProductConversionController {

    private final BoxProductConversionService conversionService;

    @GetMapping("/box-proposals")
    public ResponseEntity<List<BoxProposalResponse>> findProposals() {
        return ResponseEntity.ok(conversionService.findProposals());
    }

    @PostMapping("/{id}/box-conversion")
    public ResponseEntity<ProductResponse> convert(@PathVariable UUID id, @RequestBody BoxConversionRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(conversionService.convert(id, request, username(authentication)));
    }

    @PostMapping("/{id}/box-discard")
    public ResponseEntity<Void> discard(@PathVariable UUID id, Authentication authentication) {
        conversionService.discard(id, username(authentication));
        return ResponseEntity.noContent().build();
    }

    // The principal is our User, whose toString is not its name
    private static String username(Authentication authentication) {
        return authentication.getPrincipal() instanceof User user ? user.getUsername() : authentication.getName();
    }
}
