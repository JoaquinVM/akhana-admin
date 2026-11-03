package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.ProductWebPublicationChange;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.ProductWebPublicationChangeRepository;
import com.akhana.akhana_admin.service.ProductWebPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// CAT-13: marking is not publishing; the export also asks for an active product with a photo (WEB-01)
@Service
@RequiredArgsConstructor
@Transactional
public class ProductWebPublicationServiceImpl implements ProductWebPublicationService {

    static final String SPECIAL_NOT_ALLOWED = "Un producto especial no se publica en la web.";
    static final String DELETED_NOT_ALLOWED = "Un producto eliminado no se publica en la web.";

    private final ProductRepository productRepository;
    private final ProductWebPublicationChangeRepository changeRepository;

    @Override
    public ProductResponse mark(UUID productId, String username) {
        Product product = findProduct(productId);
        if (product.isSpecial()) {
            throw new IllegalArgumentException(SPECIAL_NOT_ALLOWED);
        }
        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalArgumentException(DELETED_NOT_ALLOWED);
        }
        return change(product, true, username);
    }

    @Override
    public ProductResponse unmark(UUID productId, String username) {
        return change(findProduct(productId), false, username);
    }

    private Product findProduct(UUID productId) {
        return productRepository.findByIdWithRelations(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
    }

    // Repeating the current state leaves no row: nothing changed
    private ProductResponse change(Product product, boolean published, String username) {
        if (product.isWebPublished() != published) {
            product.setWebPublished(published);
            productRepository.save(product);
            changeRepository.save(ProductWebPublicationChange.builder()
                .productId(product.getId())
                .published(published)
                .changedBy(username != null ? username : "SYSTEM")
                .changedAt(Instant.now())
                .build());
        }
        return ProductResponse.fromEntity(product);
    }
}
