package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.IntegrationCatalogResponse;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductPresentation;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.SaleUnit;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.IntegrationCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IntegrationCatalogServiceImpl implements IntegrationCatalogService {

    private static final String CURRENCY = "Bs";

    private final ProductRepository productRepository;

    /**
     * Active products only (CAT-03: only those are sold), without the special products of the POS (POS-11, POS-13),
     * which have no real price for a customer.
     */
    @Override
    @Transactional(readOnly = true)
    public IntegrationCatalogResponse getCatalog() {
        List<IntegrationCatalogResponse.Item> items = productRepository.findByStatusOrderByNameAsc(ProductStatus.ACTIVO)
            .stream()
            .filter(product -> product.getSpecialType() == null)
            .map(IntegrationCatalogServiceImpl::toItem)
            .toList();
        return new IntegrationCatalogResponse(Instant.now(), CURRENCY, items);
    }

    private static IntegrationCatalogResponse.Item toItem(Product product) {
        List<IntegrationCatalogResponse.Presentation> presentations = product.getSaleUnit() == SaleUnit.KILOGRAM
            ? product.getPresentations().stream()
                .filter(ProductPresentation::isActive)
                .map(p -> new IntegrationCatalogResponse.Presentation(
                    p.getName(), p.getWeightKg(), ProductPresentationServiceImpl.priceOf(p, product)))
                .toList()
            : List.of();
        return new IntegrationCatalogResponse.Item(
            product.getCode(),
            product.getName(),
            product.getCategory() != null ? product.getCategory().getName() : null,
            product.getDescription(),
            product.getSaleUnit().name(),
            product.getSellPrice().setScale(2, RoundingMode.HALF_UP),
            presentations,
            product.getUpdatedAt() != null ? product.getUpdatedAt() : product.getCreatedAt());
    }
}
