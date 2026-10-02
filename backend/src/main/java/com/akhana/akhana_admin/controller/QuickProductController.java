package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.QuickProductGroupRequest;
import com.akhana.akhana_admin.dto.QuickProductGroupResponse;
import com.akhana.akhana_admin.dto.ReorderGroupsRequest;
import com.akhana.akhana_admin.dto.ReorderItemsRequest;
import com.akhana.akhana_admin.service.QuickProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quick-products/groups")
@RequiredArgsConstructor
public class QuickProductController {

    private final QuickProductService quickProductService;

    @GetMapping
    public ResponseEntity<List<QuickProductGroupResponse>> getAllGroups() {
        return ResponseEntity.ok(quickProductService.getAllGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuickProductGroupResponse> getGroupById(@PathVariable UUID id) {
        return ResponseEntity.ok(quickProductService.getGroupById(id));
    }

    @PostMapping
    public ResponseEntity<QuickProductGroupResponse> createGroup(@Valid @RequestBody QuickProductGroupRequest request) {
        QuickProductGroupResponse response = quickProductService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuickProductGroupResponse> updateGroup(
            @PathVariable UUID id,
            @Valid @RequestBody QuickProductGroupRequest request) {
        return ResponseEntity.ok(quickProductService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        quickProductService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    public ResponseEntity<List<QuickProductGroupResponse>> reorderGroups(@Valid @RequestBody ReorderGroupsRequest request) {
        return ResponseEntity.ok(quickProductService.reorderGroups(request));
    }

    @PutMapping("/{id}/items/reorder")
    public ResponseEntity<QuickProductGroupResponse> reorderGroupItems(
            @PathVariable UUID id,
            @Valid @RequestBody ReorderItemsRequest request) {
        return ResponseEntity.ok(quickProductService.reorderGroupItems(id, request));
    }
}
