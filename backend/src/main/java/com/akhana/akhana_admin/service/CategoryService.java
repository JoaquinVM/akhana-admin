package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.CategoryRequest;
import com.akhana.akhana_admin.dto.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    List<CategoryResponse> getAllCategories(String search);

    List<CategoryResponse> getAllCategories(String search, String status);

    CategoryResponse getCategoryById(UUID id);

    CategoryResponse createCategory(CategoryRequest request, String currentUsername);

    CategoryResponse updateCategory(UUID id, CategoryRequest request, String currentUsername);

    void deleteCategory(UUID id, String currentUsername);
}
