package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.CategoryRequest;
import com.akhana.akhana_admin.dto.CategoryResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        List<CategoryResponse> categories = categoryService.getAllCategories(search, status);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID id) {
        CategoryResponse category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(category);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        CategoryResponse created = categoryService.createCategory(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        CategoryResponse updated = categoryService.updateCategory(id, request, username);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID id,
            Authentication authentication) {
        String username = extractUsername(authentication);
        categoryService.deleteCategory(id, username);
        return ResponseEntity.noContent().build();
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
