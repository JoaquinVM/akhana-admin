package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.TagRequest;
import com.akhana.akhana_admin.dto.TagResponse;

import java.util.List;
import java.util.UUID;

public interface TagService {

    List<TagResponse> getAllTags(String search);

    List<TagResponse> getAllTags(String search, String status);

    TagResponse getTagById(UUID id);

    TagResponse createTag(TagRequest request, String currentUsername);

    TagResponse updateTag(UUID id, TagRequest request, String currentUsername);

    void deleteTag(UUID id, String currentUsername);
}
