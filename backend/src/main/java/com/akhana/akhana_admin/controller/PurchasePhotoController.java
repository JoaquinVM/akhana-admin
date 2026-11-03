package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.PurchasePhotoResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.PurchasePhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

// COM-23: the Compras permission (P-33). No route ends in a file extension, which nginx treats as static
@RestController
@PreAuthorize("hasAuthority('PURCHASES')")
@RequestMapping("/api/purchases/{purchaseId}/photo")
@RequiredArgsConstructor
public class PurchasePhotoController {

    // A file never changes under its id, and the token is part of the request
    private static final String CACHE_CONTROL = "private, max-age=31536000, immutable";

    private final PurchasePhotoService photoService;

    @PostMapping
    public ResponseEntity<PurchasePhotoResponse> upload(@PathVariable UUID purchaseId,
                                                        @RequestParam("file") MultipartFile file,
                                                        Authentication authentication) throws IOException {
        UUID photoId = photoService.upload(purchaseId, file.getBytes(), extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(new PurchasePhotoResponse(photoId));
    }

    @GetMapping("/{photoId}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID purchaseId, @PathVariable UUID photoId) {
        return jpeg(photoService.readImage(purchaseId, photoId));
    }

    @GetMapping("/{photoId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID purchaseId, @PathVariable UUID photoId) {
        return jpeg(photoService.readThumbnail(purchaseId, photoId));
    }

    private ResponseEntity<byte[]> jpeg(byte[] content) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
            .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
            .body(content);
    }

    private String extractUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
