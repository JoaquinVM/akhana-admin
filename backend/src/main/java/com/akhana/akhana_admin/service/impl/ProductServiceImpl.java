package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ProductRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.CategoryRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.repository.TagRepository;
import com.akhana.akhana_admin.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(String search) {
        return getAllProducts(search, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(String search, String status) {
        List<Product> products;
        boolean hasSearch = (search != null && !search.trim().isEmpty());
        String term = hasSearch ? search.trim() : null;

        if (status != null && !status.trim().isEmpty()) {
            String normStatus = status.trim().toUpperCase();
            if ("ACTIVO".equals(normStatus) || "INACTIVO".equals(normStatus) || "ELIMINADO".equals(normStatus)) {
                ProductStatus targetStatus = ProductStatus.valueOf(normStatus);
                if (hasSearch) {
                    products = productRepository.searchProductsByStatus(term, targetStatus);
                } else {
                    products = productRepository.findByStatusOrderByNameAsc(targetStatus);
                }
                return products.stream().map(ProductResponse::fromEntity).toList();
            }
        }

        // Por defecto: Activos e Inactivos (excluye ELIMINADO)
        if (hasSearch) {
            products = productRepository.searchProducts(term, ProductStatus.ELIMINADO);
        } else {
            products = productRepository.findByStatusNotOrderByNameAsc(ProductStatus.ELIMINADO);
        }

        return products.stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        return ProductResponse.fromEntity(product);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request, String currentUsername) {
        String code = request.code().trim();
        String name = request.name().trim();

        // Validación exclusiva en Backend de unicidad de código (excluye ELIMINADO)
        if (productRepository.existsByCodeIgnoreCaseAndStatusNot(code, ProductStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe un producto activo o inactivo con el código especificado.");
        }

        // Validación exclusiva en Backend de unicidad de nombre (excluye ELIMINADO)
        if (productRepository.existsByNameIgnoreCaseAndStatusNot(name, ProductStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe un producto activo o inactivo con el nombre especificado.");
        }

        // Validar existencia de categoría (no eliminada)
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría especificada no existe con ID: " + request.categoryId()));
        if (category.getStatus() == CategoryStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar una categoría en estado ELIMINADO.");
        }

        // Validar existencia de proveedor (no eliminado)
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("El proveedor especificado no existe con ID: " + request.supplierId()));
        if (supplier.getStatus() == SupplierStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar un proveedor en estado ELIMINADO.");
        }

        // Validar y asociar etiquetas
        Set<Tag> tags = resolveTags(request.tagIds());

        // Cálculo automático de utilidades
        BigDecimal buyPrice = request.buyPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = request.sellPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fixedProfit = calculateFixedProfit(buyPrice, sellPrice);
        BigDecimal percentageProfit = calculatePercentageProfit(buyPrice, fixedProfit);

        ProductStatus status = (request.status() != null && request.status() != ProductStatus.ELIMINADO)
                ? request.status()
                : ProductStatus.ACTIVO;

        Product product = Product.builder()
                .code(code)
                .name(name)
                .category(category)
                .supplier(supplier)
                .description(request.description() != null ? request.description().trim() : null)
                .tags(tags)
                .buyPrice(buyPrice)
                .sellPrice(sellPrice)
                .fixedProfit(fixedProfit)
                .percentageProfit(percentageProfit)
                .status(status)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }

    @Override
    public ProductResponse updateProduct(UUID id, ProductRequest request, String currentUsername) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalStateException("Los productos con estado ELIMINADO no pueden ser editados.");
        }

        String code = request.code().trim();
        String name = request.name().trim();

        // Validación de unicidad de código excluyendo el actual y eliminados
        if (productRepository.existsByCodeIgnoreCaseAndStatusNotAndIdNot(code, ProductStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otro producto activo o inactivo con el código especificado.");
        }

        // Validación de unicidad de nombre excluyendo el actual y eliminados
        if (productRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(name, ProductStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otro producto activo o inactivo con el nombre especificado.");
        }

        // Validar categoría
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría especificada no existe con ID: " + request.categoryId()));
        if (category.getStatus() == CategoryStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar una categoría en estado ELIMINADO.");
        }

        // Validar proveedor
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("El proveedor especificado no existe con ID: " + request.supplierId()));
        if (supplier.getStatus() == SupplierStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar un proveedor en estado ELIMINADO.");
        }

        // Validar etiquetas
        Set<Tag> tags = resolveTags(request.tagIds());

        // Recalcular utilidades
        BigDecimal buyPrice = request.buyPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = request.sellPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal fixedProfit = calculateFixedProfit(buyPrice, sellPrice);
        BigDecimal percentageProfit = calculatePercentageProfit(buyPrice, fixedProfit);

        product.setCode(code);
        product.setName(name);
        product.setCategory(category);
        product.setSupplier(supplier);
        product.setDescription(request.description() != null ? request.description().trim() : null);
        product.setTags(tags);
        product.setBuyPrice(buyPrice);
        product.setSellPrice(sellPrice);
        product.setFixedProfit(fixedProfit);
        product.setPercentageProfit(percentageProfit);

        if (request.status() != null && request.status() != ProductStatus.ELIMINADO) {
            product.setStatus(request.status());
        }

        product.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        product.setUpdatedAt(Instant.now());

        Product updated = productRepository.save(product);
        return ProductResponse.fromEntity(updated);
    }

    @Override
    public ProductResponse changeProductStatus(UUID id, ProductStatus newStatus, String currentUsername) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalStateException("Un producto con estado ELIMINADO no puede cambiar de estado.");
        }

        if (newStatus == ProductStatus.ELIMINADO) {
            throw new IllegalArgumentException("Para eliminar el producto debe utilizar la acción de eliminación lógica.");
        }

        product.setStatus(newStatus);
        product.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        product.setUpdatedAt(Instant.now());

        Product updated = productRepository.save(product);
        return ProductResponse.fromEntity(updated);
    }

    @Override
    public void deleteProduct(UUID id, String currentUsername) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalStateException("El producto ya se encuentra en estado ELIMINADO.");
        }

        // Eliminación lógica obligatoria
        product.setStatus(ProductStatus.ELIMINADO);
        product.setDeletedBy(currentUsername != null ? currentUsername : "SYSTEM");
        product.setDeletedAt(Instant.now());

        productRepository.save(product);
    }

    private Set<Tag> resolveTags(List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Tag> foundTags = tagRepository.findAllById(tagIds);
        for (Tag tag : foundTags) {
            if (tag.getStatus() == TagStatus.ELIMINADO) {
                throw new IllegalStateException("No se puede asociar una etiqueta en estado ELIMINADO: " + tag.getName());
            }
        }
        return new HashSet<>(foundTags);
    }

    private BigDecimal calculateFixedProfit(BigDecimal buyPrice, BigDecimal sellPrice) {
        return sellPrice.subtract(buyPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePercentageProfit(BigDecimal buyPrice, BigDecimal fixedProfit) {
        if (buyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return fixedProfit.divide(buyPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
