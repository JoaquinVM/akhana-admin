package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.CategoryRequest;
import com.akhana.akhana_admin.dto.CategoryResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Category;
import com.akhana.akhana_admin.model.CategoryStatus;
import com.akhana.akhana_admin.repository.CategoryRepository;
import com.akhana.akhana_admin.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(String search) {
        return getAllCategories(search, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(String search, String status) {
        List<Category> categories;
        boolean hasSearch = (search != null && !search.trim().isEmpty());
        String term = hasSearch ? search.trim() : null;

        if (status != null && !status.trim().isEmpty()) {
            String normStatus = status.trim().toUpperCase();
            if ("ELIMINADO".equals(normStatus)) {
                if (hasSearch) {
                    categories = categoryRepository.searchCategoriesByStatus(term, CategoryStatus.ELIMINADO);
                } else {
                    categories = categoryRepository.findByStatusOrderByNameAsc(CategoryStatus.ELIMINADO);
                }
                return categories.stream().map(CategoryResponse::fromEntity).toList();
            } else if ("ACTIVO".equals(normStatus)) {
                if (hasSearch) {
                    categories = categoryRepository.searchCategoriesByStatus(term, CategoryStatus.ACTIVO);
                } else {
                    categories = categoryRepository.findByStatusOrderByNameAsc(CategoryStatus.ACTIVO);
                }
                return categories.stream().map(CategoryResponse::fromEntity).toList();
            }
        }

        // Por defecto: Categorías activas (no eliminadas)
        if (hasSearch) {
            categories = categoryRepository.searchCategories(term, CategoryStatus.ELIMINADO);
        } else {
            categories = categoryRepository.findByStatusNotOrderByNameAsc(CategoryStatus.ELIMINADO);
        }
        return categories.stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));
        return CategoryResponse.fromEntity(category);
    }

    @Override
    public CategoryResponse createCategory(CategoryRequest request, String currentUsername) {
        String name = request.name().trim();

        // Validación exclusiva en Backend de unicidad de nombre (excluye ELIMINADO)
        if (categoryRepository.existsByNameIgnoreCaseAndStatusNot(name, CategoryStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe una categoría activa con el nombre especificado.");
        }

        Category category = Category.builder()
                .name(name)
                .description(request.description() != null ? request.description().trim() : null)
                .color(request.color().trim())
                .status(CategoryStatus.ACTIVO)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        Category saved = categoryRepository.save(category);
        return CategoryResponse.fromEntity(saved);
    }

    @Override
    public CategoryResponse updateCategory(UUID id, CategoryRequest request, String currentUsername) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));

        if (category.getStatus() == CategoryStatus.ELIMINADO) {
            throw new IllegalStateException("Las categorías con estado ELIMINADO no pueden ser editadas.");
        }

        String name = request.name().trim();

        // Validación de unicidad de nombre excluyendo el registro actual y eliminados
        if (categoryRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(name, CategoryStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otra categoría activa con el nombre especificado.");
        }

        category.setName(name);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        category.setColor(request.color().trim());
        category.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        category.setUpdatedAt(Instant.now());

        Category updated = categoryRepository.save(category);
        return CategoryResponse.fromEntity(updated);
    }

    @Override
    public void deleteCategory(UUID id, String currentUsername) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));

        if (category.getStatus() == CategoryStatus.ELIMINADO) {
            throw new IllegalStateException("La categoría ya se encuentra en estado ELIMINADO.");
        }

        // Eliminación lógica obligatoria: nunca física
        category.setStatus(CategoryStatus.ELIMINADO);
        category.setDeletedBy(currentUsername != null ? currentUsername : "SYSTEM");
        category.setDeletedAt(Instant.now());

        categoryRepository.save(category);
    }
}
