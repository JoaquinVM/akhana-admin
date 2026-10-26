package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.ConsumptionRequest;
import com.akhana.akhana_admin.dto.ConsumptionResponse;
import com.akhana.akhana_admin.dto.VoidConsumptionRequest;
import com.akhana.akhana_admin.model.ConsumptionStatus;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.ConsumptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAuthority('CONSUMPTIONS')")
@RequestMapping("/api/consumptions")
@RequiredArgsConstructor
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    @GetMapping
    public ResponseEntity<List<ConsumptionResponse>> getConsumptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) ConsumptionStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(consumptionService.getConsumptions(from, to, productId, status, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConsumptionResponse> getConsumptionById(@PathVariable UUID id) {
        return ResponseEntity.ok(consumptionService.getConsumptionById(id));
    }

    @PostMapping
    public ResponseEntity<ConsumptionResponse> registerConsumption(
            @Valid @RequestBody ConsumptionRequest request,
            Authentication authentication) {
        ConsumptionResponse created = consumptionService.registerConsumption(request, extractUsername(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<ConsumptionResponse> voidConsumption(
            @PathVariable UUID id,
            @Valid @RequestBody VoidConsumptionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(consumptionService.voidConsumption(id, request, extractUsername(authentication)));
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
