package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.BarcodePendingResponse;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductBarcode;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.SaleUnit;
import com.akhana.akhana_admin.repository.ProductBarcodeRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.ProductBarcodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductBarcodeServiceImpl implements ProductBarcodeService {

    static final int MAX_BARCODES = 5;
    static final int PENDING_DAYS = 30;
    static final String INVALID = "El código de barras no es válido.";
    static final String NOT_ALLOWED = "Este producto no admite código de barras.";
    static final String REMOVE_FOR_KILO = "Quita sus códigos de barras para venderlo por kilo.";
    static final String TOO_MANY = "Un producto admite hasta 5 códigos de barras.";
    static final String ALREADY_HAS_ONE = "Este producto ya tiene código de barras.";

    private final ProductBarcodeRepository barcodeRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        return BarcodeFormat.canonicalize(barcode)
                .flatMap(barcodeRepository::findActiveByBarcode)
                .map(found -> ProductResponse.fromEntity(found.getProduct()))
                .orElseThrow(() -> new ResourceNotFoundException("El código de barras no está registrado."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BarcodePendingResponse> findPending() {
        return barcodeRepository.findPending(StoreFormat.today().minusDays(PENDING_DAYS)).stream()
                .map(row -> new BarcodePendingResponse(row.getId(), row.getCode(), row.getName(),
                        row.getSupplierName(), row.getSellPrice()))
                .toList();
    }

    @Override
    public ProductResponse addBarcode(UUID productId, String barcode, String username) {
        Product product = editableProduct(productId);
        String canonical = canonical(barcode);
        Set<String> codes = new LinkedHashSet<>(activeCodes(product));
        codes.add(canonical);
        applyList(product, codes, username);
        touch(product, username);
        return ProductResponse.fromEntity(productRepository.save(product));
    }

    @Override
    public ProductResponse markNoBarcode(UUID productId, String username) {
        Product product = editableProduct(productId);
        if (!admitsBarcodes(product)) {
            throw new IllegalArgumentException(NOT_ALLOWED);
        }
        if (!activeCodes(product).isEmpty()) {
            throw new IllegalArgumentException(ALREADY_HAS_ONE);
        }
        product.setNoBarcodeMarkedBy(actor(username));
        product.setNoBarcodeMarkedAt(Instant.now());
        return ProductResponse.fromEntity(productRepository.save(product));
    }

    @Override
    public ProductResponse clearNoBarcode(UUID productId, String username) {
        Product product = editableProduct(productId);
        clearMark(product);
        touch(product, username);
        return ProductResponse.fromEntity(productRepository.save(product));
    }

    @Override
    public void applyBarcodes(Product product, List<String> barcodes, String username) {
        Set<String> codes = new LinkedHashSet<>();
        if (barcodes == null) {
            codes.addAll(activeCodes(product));
        } else {
            barcodes.forEach(barcode -> codes.add(canonical(barcode)));
        }
        applyList(product, codes, username);
    }

    @Override
    public void deactivateAll(Product product, String username) {
        applyList(product, Set.of(), username);
    }

    private void applyList(Product product, Set<String> codes, String username) {
        if (!codes.isEmpty() && !admitsBarcodes(product)) {
            boolean keepsOne = activeCodes(product).stream().anyMatch(codes::contains);
            throw new IllegalArgumentException(keepsOne && product.getSaleUnit() == SaleUnit.KILOGRAM
                    ? REMOVE_FOR_KILO : NOT_ALLOWED);
        }
        if (codes.size() > MAX_BARCODES) {
            throw new IllegalArgumentException(TOO_MANY);
        }
        Instant now = Instant.now();
        for (ProductBarcode existing : product.getBarcodes()) {
            if (existing.isActive() && !codes.contains(existing.getBarcode())) {
                existing.setActive(false);
                existing.setDeactivatedBy(actor(username));
                existing.setDeactivatedAt(now);
            }
        }
        List<String> current = activeCodes(product);
        for (String code : codes) {
            if (!current.contains(code)) {
                add(product, code, username, now);
            }
        }
        if (!codes.isEmpty()) {
            clearMark(product);
        }
    }

    private void add(Product product, String code, String username, Instant now) {
        barcodeRepository.findActiveByBarcode(code).ifPresent(other -> {
            throw new DuplicateResourceException("El código ya es de " + other.getProduct().getName()
                    + " (" + other.getProduct().getCode() + ").");
        });
        ProductBarcode barcode = ProductBarcode.builder()
                .product(product)
                .barcode(code)
                .createdBy(actor(username))
                .createdAt(now)
                .build();
        try {
            // Two people loading the same code at once: the index stops the second one
            barcodeRepository.saveAndFlush(barcode);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("El código ya es de otro producto.");
        }
        product.getBarcodes().add(barcode);
    }

    private Product editableProduct(UUID productId) {
        Product product = productRepository.findByIdWithRelations(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalStateException("Los productos con estado ELIMINADO no pueden ser editados.");
        }
        return product;
    }

    private static boolean admitsBarcodes(Product product) {
        return !product.isSpecial() && product.getSaleUnit() == SaleUnit.UNIT;
    }

    private static String canonical(String barcode) {
        return BarcodeFormat.canonicalize(barcode).orElseThrow(() -> new IllegalArgumentException(INVALID));
    }

    private static List<String> activeCodes(Product product) {
        return product.getBarcodes().stream().filter(ProductBarcode::isActive).map(ProductBarcode::getBarcode).toList();
    }

    private static void clearMark(Product product) {
        product.setNoBarcodeMarkedBy(null);
        product.setNoBarcodeMarkedAt(null);
    }

    private static void touch(Product product, String username) {
        product.setUpdatedBy(actor(username));
        product.setUpdatedAt(Instant.now());
    }

    private static String actor(String username) {
        return username != null ? username : "SYSTEM";
    }
}
