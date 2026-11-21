package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.BarcodePendingResponse;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductBarcode;
import com.akhana.akhana_admin.model.ProductPresentation;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.SaleUnit;
import com.akhana.akhana_admin.repository.ProductBarcodeRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.ProductBarcodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
    // The store's clock (America/La_Paz): decides today, the month and the day a movement counts for
    private final Clock clock;

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
        return barcodeRepository.findPending(StoreFormat.today(clock).minusDays(PENDING_DAYS)).stream()
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
        product.setNoBarcodeMarkedAt(Instant.now(clock));
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
        Instant now = Instant.now(clock);
        product.getBarcodes().stream().filter(ProductBarcode::isActive).forEach(existing -> deactivate(existing, username, now));
    }

    @Override
    public void applyPresentationBarcode(Product product, ProductPresentation presentation, String barcode, String username) {
        String code = barcode == null || barcode.isBlank() ? null : canonical(barcode);
        Instant now = Instant.now(clock);
        Optional<ProductBarcode> current = product.getBarcodes().stream()
                .filter(b -> b.isActive() && presentation.getId() != null && presentation.getId().equals(b.getPresentationId()))
                .findFirst();
        if (current.isPresent() && current.get().getBarcode().equals(code)) {
            return;
        }
        current.ifPresent(existing -> deactivate(existing, username, now));
        if (code != null) {
            // The freed code reaches the database before the unique index sees the new one
            barcodeRepository.flush();
            add(product, presentation, code, username, now);
        }
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
        Instant now = Instant.now(clock);
        for (ProductBarcode existing : product.getBarcodes()) {
            if (existing.isActive() && existing.getPresentationId() == null && !codes.contains(existing.getBarcode())) {
                deactivate(existing, username, now);
            }
        }
        List<String> current = activeCodes(product);
        for (String code : codes) {
            if (!current.contains(code)) {
                add(product, null, code, username, now);
            }
        }
        if (!codes.isEmpty()) {
            clearMark(product);
        }
    }

    private void add(Product product, ProductPresentation presentation, String code, String username, Instant now) {
        barcodeRepository.findActiveByBarcode(code).ifPresent(other -> {
            throw new DuplicateResourceException("El código ya es de " + owner(other) + ".");
        });
        ProductBarcode barcode = ProductBarcode.builder()
                .product(product)
                .presentationId(presentation != null ? presentation.getId() : null)
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

    // «Barra Chia (NSE-BAR-CHI)», or «Barra Chia · Caja ×12» for the code of a package (CAT-15)
    private static String owner(ProductBarcode barcode) {
        Product product = barcode.getProduct();
        if (barcode.getPresentationId() != null) {
            return product.getPresentations().stream()
                    .filter(p -> p.getId().equals(barcode.getPresentationId()))
                    .findFirst()
                    .map(p -> product.getName() + " · " + p.getName())
                    .orElse(product.getName());
        }
        return product.getName() + " (" + product.getCode() + ")";
    }

    private static void deactivate(ProductBarcode barcode, String username, Instant now) {
        barcode.setActive(false);
        barcode.setDeactivatedBy(actor(username));
        barcode.setDeactivatedAt(now);
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

    // The product's own codes; those of its packages are not part of its list (CAT-15)
    private static List<String> activeCodes(Product product) {
        return product.getBarcodes().stream().filter(b -> b.isActive() && b.getPresentationId() == null)
                .map(ProductBarcode::getBarcode).toList();
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
