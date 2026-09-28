package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.TagRequest;
import com.akhana.akhana_admin.dto.TagResponse;
import com.akhana.akhana_admin.model.User;
import com.akhana.akhana_admin.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponse>> getAllTags(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        List<TagResponse> tags = tagService.getAllTags(search, status);
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> getTagById(@PathVariable UUID id) {
        TagResponse tag = tagService.getTagById(id);
        return ResponseEntity.ok(tag);
    }

    @PostMapping
    public ResponseEntity<TagResponse> createTag(
            @Valid @RequestBody TagRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        TagResponse created = tagService.createTag(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> updateTag(
            @PathVariable UUID id,
            @Valid @RequestBody TagRequest request,
            Authentication authentication) {
        String username = extractUsername(authentication);
        TagResponse updated = tagService.updateTag(id, request, username);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable UUID id,
            Authentication authentication) {
        String username = extractUsername(authentication);
        tagService.deleteTag(id, username);
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
