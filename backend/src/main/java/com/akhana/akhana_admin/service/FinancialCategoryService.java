package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialCategoryRequest;
import com.akhana.akhana_admin.dto.FinancialCategoryResponse;
import com.akhana.akhana_admin.model.FinancialCategoryType;

import java.util.List;
import java.util.UUID;

public interface FinancialCategoryService {

    List<FinancialCategoryResponse> getAllCategories(FinancialCategoryType type);

    FinancialCategoryResponse getCategoryById(UUID id);

    FinancialCategoryResponse createCategory(FinancialCategoryRequest request, String currentUsername);

    FinancialCategoryResponse updateCategory(UUID id, FinancialCategoryRequest request, String currentUsername);

    FinancialCategoryResponse setSalesDefaultCategory(UUID id, String currentUsername);

    void deleteCategory(UUID id, String currentUsername);
}
