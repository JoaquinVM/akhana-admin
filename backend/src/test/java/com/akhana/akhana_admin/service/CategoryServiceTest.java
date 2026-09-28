package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.CategoryRequest;
import com.akhana.akhana_admin.dto.CategoryResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Category;
import com.akhana.akhana_admin.model.CategoryStatus;
import com.akhana.akhana_admin.repository.CategoryRepository;
import com.akhana.akhana_admin.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category activeCategory;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        activeCategory = Category.builder()
                .id(categoryId)
                .name("Aromaterapia")
                .description("Aceites esenciales y difusores")
                .color("#164312")
                .status(CategoryStatus.ACTIVO)
                .createdBy("admin")
                .createdAt(Instant.parse("2026-09-26T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("Debe crear una categoría exitosamente con estado ACTIVO y datos de auditoría")
    void createCategory_Success() {
        CategoryRequest request = new CategoryRequest(
                "Fitoterapia",
                "Hierbas medicinales y extractos",
                "#3c6a00"
        );

        when(categoryRepository.existsByNameIgnoreCaseAndStatusNot("Fitoterapia", CategoryStatus.ELIMINADO))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Fitoterapia");
        assertThat(response.description()).isEqualTo("Hierbas medicinales y extractos");
        assertThat(response.color()).isEqualTo("#3c6a00");
        assertThat(response.status()).isEqualTo(CategoryStatus.ACTIVO);
        assertThat(response.createdBy()).isEqualTo("admin");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedBy()).isNull();
        assertThat(response.deletedBy()).isNull();

        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    @DisplayName("Debe rechazar la creación si ya existe una categoría ACTIVA con el mismo nombre (case-insensitive)")
    void createCategory_DuplicateName_ThrowsException() {
        CategoryRequest request = new CategoryRequest(
                "aromaterapia",
                "Otra descripción",
                "#5b7f52"
        );

        when(categoryRepository.existsByNameIgnoreCaseAndStatusNot("aromaterapia", CategoryStatus.ELIMINADO))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Ya existe una categoría activa");

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Debe permitir crear una categoría con nombre idéntico a una categoría ELIMINADA")
    void createCategory_NameMatchesDeleted_Success() {
        CategoryRequest request = new CategoryRequest(
                "Cosmética Natural",
                "Cremas y lociones orgánicas",
                "#2e5b27"
        );

        when(categoryRepository.existsByNameIgnoreCaseAndStatusNot("Cosmética Natural", CategoryStatus.ELIMINADO))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Cosmética Natural");
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    @DisplayName("Debe retornar lista de categorías filtrada por estado ACTIVO")
    void getAllCategories_FilterActivo() {
        when(categoryRepository.findByStatusOrderByNameAsc(CategoryStatus.ACTIVO))
                .thenReturn(List.of(activeCategory));

        List<CategoryResponse> responses = categoryService.getAllCategories(null, "ACTIVO");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Aromaterapia");
        assertThat(responses.get(0).status()).isEqualTo(CategoryStatus.ACTIVO);
    }

    @Test
    @DisplayName("Debe retornar lista de categorías filtrada por estado ELIMINADO")
    void getAllCategories_FilterEliminado() {
        Category deletedCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Herramientas")
                .color("#475569")
                .status(CategoryStatus.ELIMINADO)
                .createdBy("admin")
                .deletedBy("admin")
                .deletedAt(Instant.now())
                .build();

        when(categoryRepository.findByStatusOrderByNameAsc(CategoryStatus.ELIMINADO))
                .thenReturn(List.of(deletedCategory));

        List<CategoryResponse> responses = categoryService.getAllCategories(null, "ELIMINADO");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Herramientas");
        assertThat(responses.get(0).status()).isEqualTo(CategoryStatus.ELIMINADO);
    }

    @Test
    @DisplayName("Debe filtrar categorías por búsqueda parcial insensible a mayúsculas y estado ACTIVO")
    void getAllCategories_SearchAndStatus() {
        when(categoryRepository.searchCategoriesByStatus("aroma", CategoryStatus.ACTIVO))
                .thenReturn(List.of(activeCategory));

        List<CategoryResponse> responses = categoryService.getAllCategories("aroma", "ACTIVO");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Aromaterapia");
    }

    @Test
    @DisplayName("Debe obtener una categoría por ID")
    void getCategoryById_Success() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));

        CategoryResponse response = categoryService.getCategoryById(categoryId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(categoryId);
        assertThat(response.name()).isEqualTo("Aromaterapia");
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si la categoría no existe")
    void getCategoryById_NotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(categoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("Debe actualizar una categoría correctamente con datos de auditoría")
    void updateCategory_Success() {
        CategoryRequest updateRequest = new CategoryRequest(
                "Aromaterapia Avanzada",
                "Aceites esenciales puros y difusores ultrasónicos",
                "#3b82f6"
        );

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Aromaterapia Avanzada", CategoryStatus.ELIMINADO, categoryId))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.updateCategory(categoryId, updateRequest, "editor");

        assertThat(response.name()).isEqualTo("Aromaterapia Avanzada");
        assertThat(response.description()).isEqualTo("Aceites esenciales puros y difusores ultrasónicos");
        assertThat(response.color()).isEqualTo("#3b82f6");
        assertThat(response.updatedBy()).isEqualTo("editor");
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe rechazar la actualización si el nuevo nombre colisiona con otra categoría activa")
    void updateCategory_DuplicateName_ThrowsException() {
        CategoryRequest updateRequest = new CategoryRequest(
                "Fitoterapia",
                "Descripción",
                "#164312"
        );

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Fitoterapia", CategoryStatus.ELIMINADO, categoryId))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, updateRequest, "editor"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Ya existe otra categoría activa");

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Debe permitir actualizar una categoría manteniendo su mismo nombre sin lanzar colisión")
    void updateCategory_SameName_Success() {
        CategoryRequest updateRequest = new CategoryRequest(
                "Aromaterapia",
                "Nueva descripción solamente",
                "#164312"
        );

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(categoryRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Aromaterapia", CategoryStatus.ELIMINADO, categoryId))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.updateCategory(categoryId, updateRequest, "editor");

        assertThat(response.name()).isEqualTo("Aromaterapia");
        assertThat(response.description()).isEqualTo("Nueva descripción solamente");
        assertThat(response.updatedBy()).isEqualTo("editor");
    }

    @Test
    @DisplayName("Debe eliminar lógicamente una categoría marcándola como ELIMINADO y registrando deletedBy/At")
    void deleteCategory_Success() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        categoryService.deleteCategory(categoryId, "admin");

        assertThat(activeCategory.getStatus()).isEqualTo(CategoryStatus.ELIMINADO);
        assertThat(activeCategory.getDeletedBy()).isEqualTo("admin");
        assertThat(activeCategory.getDeletedAt()).isNotNull();

        verify(categoryRepository, times(1)).save(activeCategory);
    }

    @Test
    @DisplayName("Debe lanzar excepción si se intenta eliminar una categoría que ya está ELIMINADO")
    void deleteCategory_AlreadyDeleted_ThrowsException() {
        activeCategory.setStatus(CategoryStatus.ELIMINADO);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));

        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ELIMINADO");

        verify(categoryRepository, never()).save(any(Category.class));
    }
}
