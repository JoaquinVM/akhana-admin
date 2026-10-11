package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.FinancialCategoryRequest;
import com.akhana.akhana_admin.dto.FinancialCategoryResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.FinancialCategory;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.service.FinancialCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialCategoryServiceImpl implements FinancialCategoryService {

    private final FinancialCategoryRepository financialCategoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FinancialCategoryResponse> getAllCategories(FinancialCategoryType type) {
        List<FinancialCategory> categories;
        if (type != null) {
            categories = financialCategoryRepository.findByTypeAndDeletedAtIsNullOrderByNameAsc(type);
        } else {
            categories = financialCategoryRepository.findByDeletedAtIsNullOrderByNameAsc();
        }
        return categories.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialCategoryResponse getCategoryById(UUID id) {
        FinancialCategory category = financialCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría financiera no encontrada con ID: " + id));
        return mapToResponse(category);
    }

    @Override
    @Transactional
    public FinancialCategoryResponse createCategory(FinancialCategoryRequest request, String currentUsername) {
        String trimmedName = request.getName().trim();
        if (financialCategoryRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull(trimmedName, request.getType())) {
            throw new DuplicateResourceException("Ya existe una categoría de tipo " + request.getType() + " con el nombre: " + trimmedName);
        }

        boolean isSalesDefault = Boolean.TRUE.equals(request.getIsSalesDefault());
        if (isSalesDefault) {
            if (request.getType() != FinancialCategoryType.INGRESO) {
                throw new IllegalArgumentException("La categoría de ventas debe ser obligatoriamente de tipo INGRESO.");
            }
            financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()
                    .ifPresent(old -> {
                        old.setIsSalesDefault(false);
                        financialCategoryRepository.save(old);
                    });
        }

        String color = (request.getColor() != null && !request.getColor().isBlank())
                ? request.getColor().trim()
                : "#2E5B27";

        FinancialCategory category = FinancialCategory.builder()
                .name(trimmedName)
                .type(request.getType())
                .color(color)
                .isSalesDefault(isSalesDefault)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        return mapToResponse(financialCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public FinancialCategoryResponse updateCategory(UUID id, FinancialCategoryRequest request, String currentUsername) {
        FinancialCategory category = financialCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría financiera no encontrada con ID: " + id));

        String trimmedName = request.getName().trim();
        if (financialCategoryRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot(trimmedName, category.getType(), id)) {
            throw new DuplicateResourceException("Ya existe otra categoría de tipo " + category.getType() + " con el nombre: " + trimmedName);
        }

        category.setName(trimmedName);

        if (request.getColor() != null && !request.getColor().isBlank()) {
            category.setColor(request.getColor().trim());
        }

        if (request.getIsSalesDefault() != null) {
            boolean requestedSalesDefault = request.getIsSalesDefault();
            if (requestedSalesDefault && !Boolean.TRUE.equals(category.getIsSalesDefault())) {
                if (category.getType() != FinancialCategoryType.INGRESO) {
                    throw new IllegalArgumentException("La categoría de ventas debe ser obligatoriamente de tipo INGRESO.");
                }
                financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()
                        .ifPresent(old -> {
                            old.setIsSalesDefault(false);
                            financialCategoryRepository.save(old);
                        });
                category.setIsSalesDefault(true);
            } else if (!requestedSalesDefault && Boolean.TRUE.equals(category.getIsSalesDefault())) {
                throw new IllegalStateException("Debe existir siempre una categoría configurada como categoría de ventas. Seleccione otra categoría de ingreso antes de desactivarla.");
            }
        }

        category.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        category.setUpdatedAt(Instant.now());

        return mapToResponse(financialCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public FinancialCategoryResponse setSalesDefaultCategory(UUID id, String currentUsername) {
        FinancialCategory targetCategory = financialCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría financiera no encontrada con ID: " + id));

        if (targetCategory.getType() != FinancialCategoryType.INGRESO) {
            throw new IllegalArgumentException("Solo se pueden designar como categoría de ventas aquellas de tipo INGRESO.");
        }

        if (Boolean.TRUE.equals(targetCategory.getIsSalesDefault())) {
            return mapToResponse(targetCategory);
        }

        financialCategoryRepository.findByIsSalesDefaultTrueAndDeletedAtIsNull()
                .ifPresent(old -> {
                    old.setIsSalesDefault(false);
                    old.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
                    old.setUpdatedAt(Instant.now());
                    financialCategoryRepository.save(old);
                });

        targetCategory.setIsSalesDefault(true);
        targetCategory.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        targetCategory.setUpdatedAt(Instant.now());

        return mapToResponse(financialCategoryRepository.save(targetCategory));
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id, String currentUsername) {
        FinancialCategory category = financialCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría financiera no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(category.getIsSalesDefault())) {
            throw new IllegalStateException("No se puede eliminar la categoría configurada actualmente como categoría de ventas.");
        }

        category.setDeletedAt(Instant.now());
        category.setDeletedBy(currentUsername != null ? currentUsername : "SYSTEM");
        financialCategoryRepository.save(category);
    }

    private FinancialCategoryResponse mapToResponse(FinancialCategory category) {
        return FinancialCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .color(category.getColor())
                .isSalesDefault(category.getIsSalesDefault())
                .createdBy(category.getCreatedBy())
                .createdAt(category.getCreatedAt())
                .updatedBy(category.getUpdatedBy())
                .updatedAt(category.getUpdatedAt())
                .deletedBy(category.getDeletedBy())
                .deletedAt(category.getDeletedAt())
                .build();
    }
}
