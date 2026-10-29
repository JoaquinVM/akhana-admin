package com.akhana.akhana_admin.service.impl;

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
import com.akhana.akhana_admin.service.ProductPresentationService;
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
    private final ProductPresentationService presentationService;
    private final ProductPriceChangeRepository priceChangeRepository;
    private final CreditorRepository creditorRepository;

    static final String SELL_PRICE_REQUIRED = "El precio de venta debe ser mayor a 0.";
    static final String OWNER_REQUIRED = "Elija la dueña del producto de tercero.";
    static final String UNIDENTIFIED_EXISTS = "Ya existe un producto sin identificar.";
    static final String SPECIAL_TYPE_FIXED = "El tipo de producto y su dueña no se cambian después de crearlo.";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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

        // Code unique among the non-deleted (RG-04)
        if (productRepository.existsByCodeIgnoreCaseAndStatusNot(code, ProductStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe un producto activo o inactivo con el código especificado.");
        }

        // Name unique among the non-deleted (RG-04)
        if (productRepository.existsByNameIgnoreCaseAndStatusNot(name, ProductStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe un producto activo o inactivo con el nombre especificado.");
        }

        // The category must exist and not be deleted
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría especificada no existe con ID: " + request.categoryId()));
        if (category.getStatus() == CategoryStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar una categoría en estado ELIMINADO.");
        }

        // The supplier must exist and not be deleted
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("El proveedor especificado no existe con ID: " + request.supplierId()));
        if (supplier.getStatus() == SupplierStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar un proveedor en estado ELIMINADO.");
        }

        // Validate and attach the tags
        Set<Tag> tags = resolveTags(request.tagIds());

        // POS-11: a special product has no cost or price in the catalog and is sold by unit
        ProductSpecialType specialType = request.specialType();
        Creditor owner = null;
        if (specialType == ProductSpecialType.UNIDENTIFIED
                && productRepository.existsBySpecialTypeAndStatusNot(specialType, ProductStatus.ELIMINADO)) {
            throw new IllegalArgumentException(UNIDENTIFIED_EXISTS);
        }
        if (specialType == ProductSpecialType.THIRD_PARTY) {
            if (request.ownerCreditorId() == null) {
                throw new IllegalArgumentException(OWNER_REQUIRED);
            }
            owner = creditorRepository.findById(request.ownerCreditorId())
                    .filter(c -> c.getStatus() == CreditorStatus.ACTIVO)
                    .orElseThrow(() -> new IllegalArgumentException(OWNER_REQUIRED));
        }

        // Profit is computed, never typed (CAT-02)
        BigDecimal buyPrice = specialType != null ? ZERO : request.buyPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = specialType != null ? ZERO : requireSellPrice(request.sellPrice());
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
                .saleUnit(specialType == null && request.saleUnit() != null ? request.saleUnit() : SaleUnit.UNIT)
                .specialType(specialType)
                .ownerCreditor(owner)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        Product saved = productRepository.save(product);
        recordPriceChange(saved, null, null, product.getCreatedBy(), product.getCreatedAt());
        // Presentations need the saved product (CAT-11)
        if (specialType != null) {
            return ProductResponse.fromEntity(saved, List.of());
        }
        List<String> warnings = new ArrayList<>(costWarnings(buyPrice));
        warnings.addAll(presentationService.applyPresentations(saved, request.presentations(), null, currentUsername));
        return ProductResponse.fromEntity(saved, warnings);
    }

    @Override
    public ProductResponse updateProduct(UUID id, ProductRequest request, String currentUsername) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalStateException("Los productos con estado ELIMINADO no pueden ser editados.");
        }

        UUID ownerId = product.getOwnerCreditor() != null ? product.getOwnerCreditor().getId() : null;
        if (request.specialType() != product.getSpecialType()
                || (product.getSpecialType() == ProductSpecialType.THIRD_PARTY && !ownerId.equals(request.ownerCreditorId()))) {
            throw new IllegalArgumentException(SPECIAL_TYPE_FIXED);
        }
        boolean special = product.isSpecial();

        String code = request.code().trim();
        String name = request.name().trim();

        // Code unique among the other non-deleted products
        if (productRepository.existsByCodeIgnoreCaseAndStatusNotAndIdNot(code, ProductStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otro producto activo o inactivo con el código especificado.");
        }

        // Name unique among the other non-deleted products
        if (productRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(name, ProductStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otro producto activo o inactivo con el nombre especificado.");
        }

        // Validate the category
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría especificada no existe con ID: " + request.categoryId()));
        if (category.getStatus() == CategoryStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar una categoría en estado ELIMINADO.");
        }

        // Validate the supplier
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("El proveedor especificado no existe con ID: " + request.supplierId()));
        if (supplier.getStatus() == SupplierStatus.ELIMINADO) {
            throw new IllegalStateException("No se puede asociar un proveedor en estado ELIMINADO.");
        }

        // Validate the tags
        Set<Tag> tags = resolveTags(request.tagIds());

        BigDecimal previousBuyPrice = product.getBuyPrice();
        BigDecimal previousSellPrice = product.getSellPrice();

        // Recalcular utilidades
        BigDecimal buyPrice = special ? ZERO : request.buyPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = special ? ZERO : requireSellPrice(request.sellPrice());
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
        if (request.saleUnit() != null && !special) {
            product.setSaleUnit(request.saleUnit());
        }

        product.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        product.setUpdatedAt(Instant.now());

        if (previousBuyPrice.compareTo(buyPrice) != 0 || previousSellPrice.compareTo(sellPrice) != 0) {
            recordPriceChange(product, previousBuyPrice, previousSellPrice, product.getUpdatedBy(), product.getUpdatedAt());
        }

        List<String> warnings = new ArrayList<>(special ? List.of() : costWarnings(buyPrice));
        if (!special) {
            warnings.addAll(presentationService.applyPresentations(product, request.presentations(),
                    previousSellPrice, currentUsername));
        }
        Product updated = productRepository.save(product);
        return ProductResponse.fromEntity(updated, warnings);
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

        // Logical delete only (RG-01)
        product.setStatus(ProductStatus.ELIMINADO);
        product.setDeletedBy(currentUsername != null ? currentUsername : "SYSTEM");
        product.setDeletedAt(Instant.now());

        productRepository.save(product);
    }

    // CAT-07: cost and price changes from the catalog; null previous prices mean the product was just created
    private void recordPriceChange(Product product, BigDecimal previousBuyPrice, BigDecimal previousSellPrice,
                                   String username, Instant now) {
        priceChangeRepository.save(ProductPriceChange.builder()
                .productId(product.getId())
                .previousBuyPrice(previousBuyPrice)
                .newBuyPrice(product.getBuyPrice())
                .previousSellPrice(previousSellPrice)
                .newSellPrice(product.getSellPrice())
                .source(PriceChangeSource.CATALOG)
                .changedBy(username)
                .changedAt(now)
                .build());
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

    // POS-11: special products are only sold in the POS with a typed name and price
    static void ensureNotSpecial(Product product) {
        if (product.isSpecial()) {
            throw new IllegalArgumentException("'" + product.getName()
                    + "' es un producto especial: solo se vende en el POS con nombre y precio.");
        }
    }

    // Rounded first, so a price that rounds to 0.00 is rejected too (CAT-01)
    private static BigDecimal requireSellPrice(BigDecimal sellPrice) {
        BigDecimal rounded = sellPrice.setScale(2, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0) {
            throw new IllegalArgumentException(SELL_PRICE_REQUIRED);
        }
        return rounded;
    }

    static final String ZERO_COST_WARNING = "Costo 0: toda la venta cuenta como utilidad.";

    // CAT-01: a cost of 0 is saved but warned about
    static List<String> costWarnings(BigDecimal buyPrice) {
        return buyPrice.signum() == 0 ? List.of(ZERO_COST_WARNING) : List.of();
    }

    static BigDecimal calculateFixedProfit(BigDecimal buyPrice, BigDecimal sellPrice) {
        return sellPrice.subtract(buyPrice).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal calculatePercentageProfit(BigDecimal buyPrice, BigDecimal fixedProfit) {
        if (buyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return fixedProfit.divide(buyPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
