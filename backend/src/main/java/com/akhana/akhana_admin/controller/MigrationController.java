package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.MigrationResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.MigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

// FIN-11: Migración screen, administrator only
@RestController
@PreAuthorize("hasAuthority('MIGRATION')")
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @PostMapping("/preview")
    public ResponseEntity<MigrationResponse> preview(
            @RequestParam("recaudaciones") MultipartFile recaudaciones,
            @RequestParam("pedidos") MultipartFile pedidos,
            @RequestParam("cutoffDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cutoffDate)
            throws IOException {
        return ResponseEntity.ok(migrationService.preview(recaudaciones.getOriginalFilename(), recaudaciones.getBytes(),
            pedidos.getOriginalFilename(), pedidos.getBytes(), cutoffDate));
    }

    @PostMapping
    public ResponseEntity<MigrationResponse> importHistory(
            @RequestParam("recaudaciones") MultipartFile recaudaciones,
            @RequestParam("pedidos") MultipartFile pedidos,
            @RequestParam("cutoffDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate cutoffDate,
            Authentication authentication) throws IOException {
        return ResponseEntity.ok(migrationService.importHistory(recaudaciones.getOriginalFilename(),
            recaudaciones.getBytes(), pedidos.getOriginalFilename(), pedidos.getBytes(), cutoffDate,
            extractUsername(authentication)));
    }

    private String extractUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof User user) {
            return user.getUsername();
        }
        return authentication.getName();
    }
}
