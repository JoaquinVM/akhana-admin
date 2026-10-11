package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.FinancialCategoryRequest;
import com.akhana.akhana_admin.dto.FinancialCategoryResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.model.FinancialCategory;
import com.akhana.akhana_admin.model.FinancialCategoryType;
import com.akhana.akhana_admin.repository.FinancialCategoryRepository;
import com.akhana.akhana_admin.service.impl.FinancialCategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialCategoryServiceTest {

    @Mock
    private FinancialCategoryRepository financialCategoryRepository;

    @InjectMocks
    private FinancialCategoryServiceImpl financialCategoryService;

    private FinancialCategory salesCategory;

    @BeforeEach
    void setUp() {
        salesCategory = FinancialCategory.builder()
                .id(UUID.randomUUID())
                .name("Ventas")
                .type(FinancialCategoryType.INGRESO)
                .color("#2E5B27")
                .isSalesDefault(true)
                .build();
    }

    @Test
    @DisplayName("Debe impedir crear categoría con nombre duplicado en el mismo tipo")
    void testDuplicateCategory() {
        FinancialCategoryRequest req = FinancialCategoryRequest.builder()
                .name("Ventas")
                .type(FinancialCategoryType.INGRESO)
                .build();

        when(financialCategoryRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull("Ventas", FinancialCategoryType.INGRESO))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> financialCategoryService.createCategory(req, "admin"));
    }

    @Test
    @DisplayName("Debe impedir designar categoría de tipo GASTO como categoría de ventas")
    void testExpenseCannotBeSalesDefault() {
        FinancialCategoryRequest req = FinancialCategoryRequest.builder()
                .name("Compras")
                .type(FinancialCategoryType.GASTO)
                .isSalesDefault(true)
                .build();

        assertThrows(IllegalArgumentException.class, () -> financialCategoryService.createCategory(req, "admin"));
    }

    @Test
    @DisplayName("Debe impedir eliminar la categoría configurada actualmente como categoría de ventas")
    void testDeleteSalesDefaultForbidden() {
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(salesCategory.getId()))
                .thenReturn(Optional.of(salesCategory));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> financialCategoryService.deleteCategory(salesCategory.getId(), "admin"));
        assertTrue(ex.getMessage().contains("categoría de ventas"));
    }

    @Test
    @DisplayName("Debe crear categoría con color configurado")
    void testCreateCategoryWithColor() {
        FinancialCategoryRequest req = FinancialCategoryRequest.builder()
                .name("Marketing")
                .type(FinancialCategoryType.GASTO)
                .color("#3B82F6")
                .isSalesDefault(false)
                .build();

        when(financialCategoryRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNull("Marketing", FinancialCategoryType.GASTO))
                .thenReturn(false);
        when(financialCategoryRepository.save(any(FinancialCategory.class)))
                .thenAnswer(inv -> {
                    FinancialCategory c = inv.getArgument(0);
                    c.setId(UUID.randomUUID());
                    return c;
                });

        FinancialCategoryResponse res = financialCategoryService.createCategory(req, "admin");
        assertNotNull(res);
        assertEquals("Marketing", res.getName());
        assertEquals("#3B82F6", res.getColor());
        assertEquals(FinancialCategoryType.GASTO, res.getType());
    }

    @Test
    @DisplayName("Debe actualizar el color de una categoría existente")
    void testUpdateCategoryColor() {
        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(salesCategory.getId()))
                .thenReturn(Optional.of(salesCategory));
        when(financialCategoryRepository.existsByNameIgnoreCaseAndTypeAndDeletedAtIsNullAndIdNot("Ventas Actualizadas", salesCategory.getType(), salesCategory.getId()))
                .thenReturn(false);
        when(financialCategoryRepository.save(any(FinancialCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FinancialCategoryRequest req = FinancialCategoryRequest.builder()
                .name("Ventas Actualizadas")
                .type(FinancialCategoryType.INGRESO)
                .color("#10B981")
                .build();

        FinancialCategoryResponse res = financialCategoryService.updateCategory(salesCategory.getId(), req, "admin");
        assertNotNull(res);
        assertEquals("#10B981", res.getColor());
        assertEquals("Ventas Actualizadas", res.getName());
    }

    @Test
    @DisplayName("Debe eliminar lógicamente una categoría normal")
    void testDeleteNormalCategory() {
        FinancialCategory normal = FinancialCategory.builder()
                .id(UUID.randomUUID())
                .name("Servicios")
                .type(FinancialCategoryType.GASTO)
                .color("#EF4444")
                .isSalesDefault(false)
                .build();

        when(financialCategoryRepository.findByIdAndDeletedAtIsNull(normal.getId()))
                .thenReturn(Optional.of(normal));

        financialCategoryService.deleteCategory(normal.getId(), "admin");

        assertNotNull(normal.getDeletedAt());
        verify(financialCategoryRepository).save(normal);
    }
}
