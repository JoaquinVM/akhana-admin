package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CategoryRepository;
import com.akhana.akhana_admin.repository.CreditorRepository;
import com.akhana.akhana_admin.repository.ProductPriceChangeRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.repository.TagRepository;
import com.akhana.akhana_admin.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock
    private ProductPresentationService presentationService;
    @Mock
    private ProductPriceChangeRepository priceChangeRepository;

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
                ProductStatus.ACTIVO,
                null
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
        assertThat(response.saleUnit()).isEqualTo(SaleUnit.UNIT);
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    @DisplayName("CAT-01: crea con costo 0, utilidad porcentual 0 y el aviso de costo 0")
    void createProduct_ZeroCost_SavesWithWarning() {
        ProductRequest request = new ProductRequest("BON-001", "Lip care frutos rojos", categoryId, supplierId,
                null, null, new BigDecimal("0"), new BigDecimal("15.00"), ProductStatus.ACTIVO, null);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.createProduct(request, "admin");

        assertThat(response.buyPrice()).isEqualByComparingTo("0.00");
        assertThat(response.fixedProfit()).isEqualByComparingTo("15.00");
        assertThat(response.percentageProfit()).isEqualByComparingTo("0.00");
        assertThat(response.warnings()).containsExactly("Costo 0: toda la venta cuenta como utilidad.");
    }

    @Test
    @DisplayName("CAT-01: al editar a costo 0 también avisa")
    void updateProduct_ZeroCost_Warns() {
        ProductRequest request = new ProductRequest("PROD-001", "Té Matcha Ceremonial 100g", categoryId, supplierId,
                null, null, BigDecimal.ZERO, new BigDecimal("50.00"), ProductStatus.ACTIVO, null);
        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(activeProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.updateProduct(productId, request, "admin");

        assertThat(response.warnings()).containsExactly("Costo 0: toda la venta cuenta como utilidad.");
    }

    @Test
    @DisplayName("CAT-07: crear un producto registra su costo y precio iniciales con origen CATALOG")
    void createProduct_RecordsInitialPrices() {
        ProductRequest request = new ProductRequest("PROD-002", "Té Chai Especial", categoryId, supplierId,
                null, null, new BigDecimal("40"), new BigDecimal("50"), ProductStatus.ACTIVO, null);
        UUID newId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(newId);
            return p;
        });

        productService.createProduct(request, "operador");

        ArgumentCaptor<ProductPriceChange> captor = ArgumentCaptor.forClass(ProductPriceChange.class);
        verify(priceChangeRepository).save(captor.capture());
        ProductPriceChange change = captor.getValue();
        assertThat(change.getProductId()).isEqualTo(newId);
        assertThat(change.getSource()).isEqualTo(PriceChangeSource.CATALOG);
        assertThat(change.getPreviousBuyPrice()).isNull();
        assertThat(change.getPreviousSellPrice()).isNull();
        assertThat(change.getNewBuyPrice()).isEqualByComparingTo("40.00");
        assertThat(change.getNewSellPrice()).isEqualByComparingTo("50.00");
        assertThat(change.getPurchaseId()).isNull();
        assertThat(change.getPresentationId()).isNull();
        assertThat(change.getChangedBy()).isEqualTo("operador");
        assertThat(change.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("CAT-07: editar costo o precio registra el anterior y el nuevo con origen CATALOG")
    void updateProduct_PriceChange_RecordsCatalogChange() {
        ProductRequest request = new ProductRequest("PROD-001", "Té Matcha Ceremonial 100g", categoryId, supplierId,
                null, null, new BigDecimal("40.00"), new BigDecimal("55.5"), ProductStatus.ACTIVO, null);
        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(activeProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(productId, request, "operador");

        ArgumentCaptor<ProductPriceChange> captor = ArgumentCaptor.forClass(ProductPriceChange.class);
        verify(priceChangeRepository).save(captor.capture());
        ProductPriceChange change = captor.getValue();
        assertThat(change.getProductId()).isEqualTo(productId);
        assertThat(change.getSource()).isEqualTo(PriceChangeSource.CATALOG);
        assertThat(change.getPreviousBuyPrice()).isEqualByComparingTo("40.00");
        assertThat(change.getNewBuyPrice()).isEqualByComparingTo("40.00");
        assertThat(change.getPreviousSellPrice()).isEqualByComparingTo("50.00");
        assertThat(change.getNewSellPrice()).isEqualByComparingTo("55.50");
        assertThat(change.getChangedBy()).isEqualTo("operador");
    }

    @Test
    @DisplayName("CAT-07: editar sin cambiar costo ni precio no registra nada")
    void updateProduct_SamePrices_RecordsNothing() {
        ProductRequest request = new ProductRequest("PROD-001", "Té Matcha renombrado", categoryId, supplierId,
                null, null, new BigDecimal("40"), new BigDecimal("50.0"), ProductStatus.ACTIVO, null);
        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(activeProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(productId, request, "operador");

        verifyNoInteractions(priceChangeRepository);
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
                ProductStatus.ACTIVO,
                null
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
                ProductStatus.ACTIVO,
                null
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
                ProductStatus.ACTIVO,
                null
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
                ProductStatus.ACTIVO,
                null
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
                ProductStatus.ACTIVO,
                SaleUnit.KILOGRAM
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
        assertThat(activeProduct.getSaleUnit()).isEqualTo(SaleUnit.KILOGRAM);
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

    // ---------------------------------------------------------------- POS-11

    @Mock
    private CreditorRepository creditorRepository;

    private ProductRequest specialRequest(ProductSpecialType type, UUID ownerId) {
        return new ProductRequest("SIN-IDENT", "Producto sin identificar", categoryId, supplierId, null, List.of(),
                new BigDecimal("5.00"), new BigDecimal("9.00"), ProductStatus.ACTIVO, SaleUnit.KILOGRAM, null, type, ownerId);
    }

    private void stubCreate() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("POS-11: el producto sin identificar se guarda con costo y precio 0, por unidad")
    void createUnidentified_zeroPricesByUnit() {
        stubCreate();

        ProductResponse response = productService.createProduct(specialRequest(ProductSpecialType.UNIDENTIFIED, null), "admin");

        assertThat(response.specialType()).isEqualTo(ProductSpecialType.UNIDENTIFIED);
        assertThat(response.buyPrice()).isEqualByComparingTo("0");
        assertThat(response.sellPrice()).isEqualByComparingTo("0");
        assertThat(response.saleUnit()).isEqualTo(SaleUnit.UNIT);
        verify(presentationService, never()).applyPresentations(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POS-11: solo un producto sin identificar y el de tercero exige dueña activa")
    void createSpecial_rules() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(productRepository.existsBySpecialTypeAndStatusNot(ProductSpecialType.UNIDENTIFIED, ProductStatus.ELIMINADO))
                .thenReturn(true);
        assertThatThrownBy(() -> productService.createProduct(specialRequest(ProductSpecialType.UNIDENTIFIED, null), "admin"))
                .hasMessage("Ya existe un producto sin identificar.");
        assertThatThrownBy(() -> productService.createProduct(specialRequest(ProductSpecialType.THIRD_PARTY, null), "admin"))
                .hasMessage("Elija la dueña del producto de tercero.");

        Creditor mami = Creditor.builder().id(UUID.randomUUID()).name("Mami").status(CreditorStatus.ACTIVO).build();
        when(creditorRepository.findById(mami.getId())).thenReturn(Optional.of(mami));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductResponse response = productService.createProduct(specialRequest(ProductSpecialType.THIRD_PARTY, mami.getId()), "admin");
        assertThat(response.ownerCreditorName()).isEqualTo("Mami");
    }

    @Test
    @DisplayName("POS-11: el tipo no cambia al editar y un producto normal sigue pidiendo precio mayor a 0")
    void update_keepsTypeAndRequiresPrice() {
        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(activeProduct));
        assertThatThrownBy(() -> productService.updateProduct(productId, specialRequest(ProductSpecialType.UNIDENTIFIED, null), "admin"))
                .hasMessage("El tipo de producto y su dueña no se cambian después de crearlo.");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(activeCategory));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        ProductRequest zeroPrice = new ProductRequest("PROD-001", "Té Matcha Ceremonial 100g", categoryId, supplierId,
                null, List.of(), new BigDecimal("40.00"), BigDecimal.ZERO, ProductStatus.ACTIVO, SaleUnit.UNIT);
        assertThatThrownBy(() -> productService.updateProduct(productId, zeroPrice, "admin"))
                .hasMessage("El precio de venta debe ser mayor a 0.");
    }
}
