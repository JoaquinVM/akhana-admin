package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.InventoryService;
import com.akhana.akhana_admin.service.StockCountService;
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

/** V2 · Inventario: Existencias, Kardex and Conteo, each with the permission of its screen (P-33). */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final StockCountService stockCountService;

    // INV-01, INV-14
    @PreAuthorize("hasAuthority('INVENTORY_STOCK')")
    @GetMapping("/stock")
    public ResponseEntity<List<StockItemResponse>> getStock(@RequestParam(required = false) UUID categoryId) {
        return ResponseEntity.ok(inventoryService.getStock(categoryId));
    }

    // INV-11: «Quedan N» in the POS; Consumos asks with it too (INV-10)
    @PreAuthorize("hasAnyAuthority('POS', 'CONSUMPTIONS', 'INVENTORY_STOCK')")
    @GetMapping("/levels")
    public ResponseEntity<List<StockLevelResponse>> getLevels() {
        return ResponseEntity.ok(inventoryService.getLevels());
    }

    // INV-03
    @PreAuthorize("hasAuthority('INVENTORY_KARDEX')")
    @GetMapping("/products/{productId}/kardex")
    public ResponseEntity<KardexResponse> getKardex(
            @PathVariable UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(inventoryService.getKardex(productId, from, to));
    }

    // INV-02
    @PreAuthorize("hasAuthority('INVENTORY_KARDEX')")
    @PostMapping("/products/{productId}/adjustments")
    public ResponseEntity<KardexResponse> adjust(@PathVariable UUID productId,
                                                 @Valid @RequestBody StockAdjustmentRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(inventoryService.adjust(productId, request, username(authentication)));
    }

    // INV-05, INV-18
    @PreAuthorize("hasAuthority('INVENTORY_COUNTS')")
    @GetMapping("/counts")
    public ResponseEntity<List<StockCountResponse>> getCounts() {
        return ResponseEntity.ok(stockCountService.list());
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNTS')")
    @PostMapping("/counts")
    public ResponseEntity<StockCountResponse> createCount(@RequestBody(required = false) StockCountRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(stockCountService.create(request, username(authentication)));
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNTS')")
    @GetMapping("/counts/{countId}")
    public ResponseEntity<StockCountResponse> getCount(@PathVariable UUID countId) {
        return ResponseEntity.ok(stockCountService.get(countId));
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNTS')")
    @PutMapping("/counts/{countId}/lines/{productId}")
    public ResponseEntity<StockCountResponse> saveLine(@PathVariable UUID countId, @PathVariable UUID productId,
                                                       @RequestBody StockCountLineRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(stockCountService.saveLine(countId, productId, request, username(authentication)));
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNTS')")
    @GetMapping("/counts/{countId}/review")
    public ResponseEntity<StockCountReviewResponse> review(@PathVariable UUID countId) {
        return ResponseEntity.ok(stockCountService.review(countId));
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNTS')")
    @PostMapping("/counts/{countId}/confirm")
    public ResponseEntity<StockCountResponse> confirm(@PathVariable UUID countId, Authentication authentication) {
        return ResponseEntity.ok(stockCountService.confirm(countId, username(authentication)));
    }

    @PreAuthorize("hasAuthority('INVENTORY_COUNTS')")
    @PostMapping("/counts/{countId}/discard")
    public ResponseEntity<StockCountResponse> discard(@PathVariable UUID countId, Authentication authentication) {
        return ResponseEntity.ok(stockCountService.discard(countId, username(authentication)));
    }

    private static String username(Authentication authentication) {
        return authentication.getPrincipal() instanceof User user ? user.getUsername() : authentication.getName();
    }
}
