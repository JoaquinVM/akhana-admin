package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.ProductPhotoResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.ProductPhotoService;
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

// CAT-12: the Products screen permission (P-33). No route ends in a file extension, which nginx treats as static
@RestController
@PreAuthorize("hasAuthority('PRODUCTS')")
@RequestMapping("/api/products/{productId}/photo")
@RequiredArgsConstructor
public class ProductPhotoController {

    // A file never changes under its id, and the token is part of the request
    private static final String CACHE_CONTROL = "private, max-age=31536000, immutable";

    private final ProductPhotoService photoService;

    @PostMapping
    public ResponseEntity<ProductPhotoResponse> upload(@PathVariable UUID productId,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) UUID expectedPhotoId,
                                                       Authentication authentication) throws IOException {
        UUID photoId = photoService.upload(productId, file.getBytes(), expectedPhotoId, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProductPhotoResponse(photoId));
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@PathVariable UUID productId,
                                       @RequestParam UUID expectedPhotoId,
                                       Authentication authentication) {
        photoService.remove(productId, expectedPhotoId, extractUsername(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{photoId}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID productId, @PathVariable UUID photoId) {
        return jpeg(photoService.readImage(productId, photoId));
    }

    @GetMapping("/{photoId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID productId, @PathVariable UUID photoId) {
        return jpeg(photoService.readThumbnail(productId, photoId));
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
