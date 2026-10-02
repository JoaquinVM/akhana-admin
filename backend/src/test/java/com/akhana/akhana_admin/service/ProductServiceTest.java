package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CategoryRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.repository.TagRepository;
import com.akhana.akhana_admin.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private UUID productId;
    private UUID categoryId;
    private UUID supplierId;
    private UUID tagId;

    private Category activeCategory;
    private Supplier activeSupplier;
    private Tag activeTag;
    private Product activeProduct;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        tagId = UUID.randomUUID();

        activeCategory = Category.builder()
                .id(categoryId)
                .name("Infusiones y Té")
                .color("#1B3B18")
                .status(CategoryStatus.ACTIVO)
                .build();

        activeSupplier = Supplier.builder()
                .id(supplierId)
                .name("Distribuidora Botánica")
                .code("PROV-001")
                .status(SupplierStatus.ACTIVO)
                .build();

        activeTag = Tag.builder()
                .id(tagId)
                .name("Orgánico")
                .color("#7BB142")
                .status(TagStatus.ACTIVO)
                .build();

        activeProduct = Product.builder()
                .id(productId)
                .code("PROD-001")
                .name("Té Matcha Ceremonial 100g")
                .category(activeCategory)
                .supplier(activeSupplier)
                .description("Té verde orgánico molido de grado ceremonial.")
                .tags(new HashSet<>(List.of(activeTag)))
                .buyPrice(new BigDecimal("40.00"))
                .sellPrice(new BigDecimal("50.00"))
                .fixedProfit(new BigDecimal("10.00"))
                .percentageProfit(new BigDecimal("25.00"))
                .status(ProductStatus.ACTIVO)
                .createdBy("admin")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Debe listar productos no eliminados ordenados por nombre A-Z por defecto")
    void getAllProducts_Default_ReturnsNonDeleted() {
        when(productRepository.findByStatusNotOrderByNameAsc(ProductStatus.ELIMINADO))
                .thenReturn(List.of(activeProduct));

        List<ProductResponse> result = productService.getAllProducts(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Té Matcha Ceremonial 100g");
        assertThat(result.getFirst().categoryName()).isEqualTo("Infusiones y Té");
        assertThat(result.getFirst().categoryColor()).isEqualTo("#1B3B18");
        assertThat(result.getFirst().supplierName()).isEqualTo("Distribuidora Botánica");
        assertThat(result.getFirst().tags()).hasSize(1);
        assertThat(result.getFirst().tags().getFirst().name()).isEqualTo("Orgánico");
    }

    @Test
    @DisplayName("Debe filtrar productos por estado específico")
    void getAllProducts_ByStatus_ReturnsFiltered() {
        when(productRepository.findByStatusOrderByNameAsc(ProductStatus.ACTIVO))
                .thenReturn(List.of(activeProduct));

        List<ProductResponse> result = productService.getAllProducts(null, "ACTIVO");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(ProductStatus.ACTIVO);
    }

    @Test
    @DisplayName("Debe buscar productos por código o nombre coincidentes")
    void getAllProducts_Search_MatchesCodeOrName() {
        when(productRepository.searchProducts("matcha", ProductStatus.ELIMINADO))
                .thenReturn(List.of(activeProduct));

        List<ProductResponse> result = productService.getAllProducts("matcha", null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().code()).isEqualTo("PROD-001");
    }

    @Test
    @DisplayName("Debe crear producto exitosamente calculando utilidades de compra 40 y venta 50")
    void createProduct_Success_CalculatesProfit() {
        ProductRequest request = new ProductRequest(
                "PROD-002",
                "Té Chai Especial",
                categoryId,
                supplierId,
                "Chai con especias selectas",
                List.of(tagId),
                new BigDecimal("40.00"),
                new BigDecimal("50.00"),
                ProductStatus.ACTIVO
        );

        when(productRepository.existsByCodeIgnoreCaseAndStatusNot("PROD-002", ProductStatus.ELIMINADO)).thenReturn(false);
        when(productRepository.existsByNameIgnoreCaseAndStatusNot("Té Chai Especial", ProductStatus.ELIMINADO)).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(activeTag));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse response = productService.createProduct(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("PROD-002");
        assertThat(response.fixedProfit()).isEqualByComparingTo("10.00");
        assertThat(response.percentageProfit()).isEqualByComparingTo("25.00");
        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVO);
    }

    @Test
    @DisplayName("Debe rechazar creación con código duplicado en producto no eliminado")
    void createProduct_DuplicateCode_ThrowsException() {
        ProductRequest request = new ProductRequest(
                "PROD-001",
                "Otro Producto",
                categoryId,
                supplierId,
                null,
                null,
                new BigDecimal("10.00"),
                new BigDecimal("15.00"),
                ProductStatus.ACTIVO
        );

        when(productRepository.existsByCodeIgnoreCaseAndStatusNot("PROD-001", ProductStatus.ELIMINADO)).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Ya existe un producto activo o inactivo con el código especificado.");
    }

    @Test
    @DisplayName("Debe rechazar creación con nombre duplicado en producto no eliminado")
    void createProduct_DuplicateName_ThrowsException() {
        ProductRequest request = new ProductRequest(
                "PROD-999",
                "Té Matcha Ceremonial 100g",
                categoryId,
                supplierId,
                null,
                null,
                new BigDecimal("10.00"),
                new BigDecimal("15.00"),
                ProductStatus.ACTIVO
        );

        when(productRepository.existsByCodeIgnoreCaseAndStatusNot("PROD-999", ProductStatus.ELIMINADO)).thenReturn(false);
        when(productRepository.existsByNameIgnoreCaseAndStatusNot("Té Matcha Ceremonial 100g", ProductStatus.ELIMINADO)).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Ya existe un producto activo o inactivo con el nombre especificado.");
    }

    @Test
    @DisplayName("Debe rechazar asociación con categoría eliminada")
    void createProduct_DeletedCategory_ThrowsException() {
        Category deletedCategory = Category.builder()
                .id(categoryId)
                .name("Cat Eliminada")
                .status(CategoryStatus.ELIMINADO)
                .build();

        ProductRequest request = new ProductRequest(
                "PROD-NEW",
                "Nuevo Producto",
                categoryId,
                supplierId,
                null,
                null,
                new BigDecimal("10.00"),
                new BigDecimal("15.00"),
                ProductStatus.ACTIVO
        );

        when(productRepository.existsByCodeIgnoreCaseAndStatusNot("PROD-NEW", ProductStatus.ELIMINADO)).thenReturn(false);
        when(productRepository.existsByNameIgnoreCaseAndStatusNot("Nuevo Producto", ProductStatus.ELIMINADO)).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(deletedCategory));

        assertThatThrownBy(() -> productService.createProduct(request, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se puede asociar una categoría en estado ELIMINADO.");
    }

    @Test
    @DisplayName("Debe rechazar asociación con etiqueta eliminada")
    void createProduct_DeletedTag_ThrowsException() {
        Tag deletedTag = Tag.builder()
                .id(tagId)
                .name("Etiqueta Vieja")
                .color("#000000")
                .status(TagStatus.ELIMINADO)
                .build();

        ProductRequest request = new ProductRequest(
                "PROD-NEW",
                "Nuevo Producto",
                categoryId,
                supplierId,
                null,
                List.of(tagId),
                new BigDecimal("10.00"),
                new BigDecimal("15.00"),
                ProductStatus.ACTIVO
        );

        when(productRepository.existsByCodeIgnoreCaseAndStatusNot("PROD-NEW", ProductStatus.ELIMINADO)).thenReturn(false);
        when(productRepository.existsByNameIgnoreCaseAndStatusNot("Nuevo Producto", ProductStatus.ELIMINADO)).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(deletedTag));

        assertThatThrownBy(() -> productService.createProduct(request, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se puede asociar una etiqueta en estado ELIMINADO");
    }

    @Test
    @DisplayName("Debe actualizar producto recalculando utilidades")
    void updateProduct_Success_RecalculatesProfit() {
        ProductRequest updateReq = new ProductRequest(
                "PROD-001",
                "Té Matcha Ceremonial 100g",
                categoryId,
                supplierId,
                "Descripción editada",
                List.of(tagId),
                new BigDecimal("50.00"),
                new BigDecimal("75.00"),
                ProductStatus.ACTIVO
        );

        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(activeProduct));
        when(productRepository.existsByCodeIgnoreCaseAndStatusNotAndIdNot("PROD-001", ProductStatus.ELIMINADO, productId)).thenReturn(false);
        when(productRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot("Té Matcha Ceremonial 100g", ProductStatus.ELIMINADO, productId)).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(activeTag));
        when(productRepository.save(any(Product.class))).thenReturn(activeProduct);

        ProductResponse response = productService.updateProduct(productId, updateReq, "admin");

        assertThat(response).isNotNull();
        assertThat(activeProduct.getFixedProfit()).isEqualByComparingTo("25.00");
        assertThat(activeProduct.getPercentageProfit()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Debe cambiar estado conmutando entre ACTIVO e INACTIVO")
    void changeProductStatus_Success() {
        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(activeProduct));
        when(productRepository.save(any(Product.class))).thenReturn(activeProduct);

        ProductResponse response = productService.changeProductStatus(productId, ProductStatus.INACTIVO, "admin");

        assertThat(response.status()).isEqualTo(ProductStatus.INACTIVO);
        assertThat(activeProduct.getStatus()).isEqualTo(ProductStatus.INACTIVO);
    }

    @Test
    @DisplayName("Debe realizar eliminación lógica estableciendo status ELIMINADO y auditoría")
    void deleteProduct_LogicalDeletion_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(activeProduct));

        productService.deleteProduct(productId, "admin");

        assertThat(activeProduct.getStatus()).isEqualTo(ProductStatus.ELIMINADO);
        assertThat(activeProduct.getDeletedBy()).isEqualTo("admin");
        assertThat(activeProduct.getDeletedAt()).isNotNull();
    }
}
