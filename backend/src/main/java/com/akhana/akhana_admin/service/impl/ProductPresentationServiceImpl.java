package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductPresentationRepository;
import com.akhana.akhana_admin.repository.ProductPriceChangeRepository;
import com.akhana.akhana_admin.service.ProductPresentationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CAT-11: presentations of products sold by weight. The product form sends the whole list; what is left out is
 * deactivated, never deleted, because sales and consumptions keep pointing to it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductPresentationServiceImpl implements ProductPresentationService {

    static final int MAX_ACTIVE = 6;
    static final String NAME_REQUIRED = "El nombre de la presentación es obligatorio.";
    static final String NAME_TOO_LONG = "El nombre de la presentación no debe superar los 50 caracteres.";
    static final String WEIGHT_POSITIVE = "El peso debe ser mayor a 0 kg.";
    static final String WEIGHT_SCALE = "El peso admite hasta 3 decimales (gramos).";
    static final String FIXED_PRICE_POSITIVE = "El precio fijo debe ser mayor a 0.";
    static final String FIXED_PRICE_SCALE = "El precio fijo admite hasta 2 decimales.";
    static final String TOO_MANY = "Un producto admite hasta " + MAX_ACTIVE + " presentaciones activas.";
    static final String NOT_OF_PRODUCT = "La presentación no pertenece a este producto.";

    private final ProductPresentationRepository presentationRepository;
    private final ProductPriceChangeRepository priceChangeRepository;

    @Override
    public List<String> applyPresentations(Product product, List<ProductPresentationRequest> requests,
                                           BigDecimal previousSellPrice, String username) {
        Instant now = Instant.now();
        if (product.getSaleUnit() != SaleUnit.KILOGRAM) {
            // A product by unit has no presentations; they come back inactive if it is sold by weight again
            product.getPresentations().stream()
                .filter(ProductPresentation::isActive)
                .forEach(p -> touch(p, username, now).setActive(false));
            return List.of();
        }

        if (requests != null) {
            applyRequests(product, requests, username, now);
        }
        product.getPresentations().sort(Comparator.comparing(ProductPresentation::getWeightKg));

        List<String> warnings = new ArrayList<>();
        List<ProductPresentation> active = product.getPresentations().stream().filter(ProductPresentation::isActive).toList();
        for (ProductPresentation presentation : active) {
            if (priceOf(presentation, product).compareTo(costOf(presentation, product)) < 0) {
                warnings.add("La presentación '" + presentation.getName() + "' se vende a pérdida.");
            }
        }
        boolean priceChanged = previousSellPrice != null && previousSellPrice.compareTo(product.getSellPrice()) != 0;
        List<ProductPresentation> fixed = active.stream().filter(p -> p.getFixedPrice() != null).toList();
        if (priceChanged && !fixed.isEmpty()) {
            warnings.add("Estas presentaciones tienen precio fijo y no cambian: " + fixed.stream()
                .map(p -> p.getName() + " (Bs " + amount(p.getFixedPrice()) + ")")
                .collect(Collectors.joining(", ")) + ".");
        }
        return warnings;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPresentation findActivePresentation(Product product, UUID presentationId) {
        ProductPresentation presentation = presentationRepository.findById(presentationId)
            .filter(p -> p.getProduct().getId().equals(product.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Presentación no encontrada con ID: " + presentationId));
        if (!presentation.isActive() || product.getSaleUnit() != SaleUnit.KILOGRAM) {
            throw new IllegalArgumentException("La presentación '" + presentation.getName() + "' de '"
                + product.getName() + "' ya no está disponible.");
        }
        return presentation;
    }

    /** Fixed price, or weight x price per kilo (CAT-11). */
    public static BigDecimal priceOf(ProductPresentation presentation, Product product) {
        if (presentation.getFixedPrice() != null) {
            return presentation.getFixedPrice().setScale(2, RoundingMode.HALF_UP);
        }
        return presentation.getWeightKg().multiply(product.getSellPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Always weight x cost per kilo: a new cost from a purchase (COM-03) reaches every presentation. */
    public static BigDecimal costOf(ProductPresentation presentation, Product product) {
        return presentation.getWeightKg().multiply(product.getBuyPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    private void applyRequests(Product product, List<ProductPresentationRequest> requests, String username, Instant now) {
        Map<UUID, ProductPresentation> current = product.getPresentations().stream()
            .collect(Collectors.toMap(ProductPresentation::getId, p -> p));
        Set<UUID> requestedIds = new HashSet<>();
        List<ProductPresentation> finalActive = new ArrayList<>();
        Map<ProductPresentation, BigDecimal> previousFixedPrices = new HashMap<>();
        List<ProductPresentation> created = new ArrayList<>();

        for (ProductPresentationRequest request : requests) {
            boolean active = request.active() == null || request.active();
            ProductPresentation presentation;
            if (request.id() != null) {
                presentation = current.get(request.id());
                if (presentation == null) {
                    throw new IllegalArgumentException(NOT_OF_PRODUCT);
                }
                requestedIds.add(request.id());
            } else if (!active) {
                continue;
            } else {
                presentation = ProductPresentation.builder().product(product).createdBy(username).createdAt(now).build();
                created.add(presentation);
            }

            if (!active) {
                if (presentation.isActive()) {
                    touch(presentation, username, now).setActive(false);
                }
                continue;
            }

            String name = validName(request.name());
            BigDecimal weight = validWeight(request.weightKg());
            BigDecimal fixedPrice = validFixedPrice(request.fixedPrice());
            boolean changed = presentation.getId() == null
                || !presentation.isActive()
                || !name.equals(presentation.getName())
                || weight.compareTo(presentation.getWeightKg()) != 0
                || !sameAmount(fixedPrice, presentation.getFixedPrice());
            if (presentation.getId() != null && !sameAmount(fixedPrice, presentation.getFixedPrice())) {
                previousFixedPrices.put(presentation, presentation.getFixedPrice());
            }
            if (presentation.getId() == null && fixedPrice != null) {
                previousFixedPrices.put(presentation, null);
            }
            presentation.setName(name);
            presentation.setWeightKg(weight);
            presentation.setFixedPrice(fixedPrice);
            presentation.setActive(true);
            if (changed && presentation.getId() != null) {
                touch(presentation, username, now);
            }
            finalActive.add(presentation);
        }

        // Left out of the form: deactivated
        current.values().stream()
            .filter(p -> p.isActive() && !requestedIds.contains(p.getId()))
            .forEach(p -> touch(p, username, now).setActive(false));

        validateActiveSet(finalActive);

        // Deactivations reach the database before the inserts, so a re-created name or weight does not hit the
        // partial unique indexes (Hibernate flushes inserts before updates)
        presentationRepository.flush();
        for (ProductPresentation presentation : created) {
            product.getPresentations().add(presentationRepository.save(presentation));
        }
        previousFixedPrices.forEach((presentation, previous) -> priceChangeRepository.save(ProductPriceChange.builder()
            .productId(product.getId())
            .previousBuyPrice(product.getBuyPrice())
            .newBuyPrice(product.getBuyPrice())
            .previousSellPrice(product.getSellPrice())
            .newSellPrice(product.getSellPrice())
            .source(PriceChangeSource.CATALOG)
            .presentationId(presentation.getId())
            .previousFixedPrice(previous)
            .newFixedPrice(presentation.getFixedPrice())
            .changedBy(username)
            .changedAt(now)
            .build()));
    }

    private static void validateActiveSet(List<ProductPresentation> active) {
        if (active.size() > MAX_ACTIVE) {
            throw new IllegalArgumentException(TOO_MANY);
        }
        Set<String> names = new HashSet<>();
        Set<BigDecimal> weights = new HashSet<>();
        for (ProductPresentation presentation : active) {
            if (!names.add(presentation.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Ya existe la presentación '" + presentation.getName() + "' en este producto.");
            }
            if (!weights.add(presentation.getWeightKg().setScale(3, RoundingMode.UNNECESSARY))) {
                throw new IllegalArgumentException("Ya existe una presentación de "
                    + presentation.getWeightKg().setScale(3, RoundingMode.UNNECESSARY).toPlainString().replace('.', ',')
                    + " kg en este producto.");
            }
        }
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(NAME_REQUIRED);
        }
        String trimmed = name.trim();
        if (trimmed.length() > 50) {
            throw new IllegalArgumentException(NAME_TOO_LONG);
        }
        return trimmed;
    }

    private static BigDecimal validWeight(BigDecimal weight) {
        if (weight == null || weight.signum() <= 0) {
            throw new IllegalArgumentException(WEIGHT_POSITIVE);
        }
        if (weight.stripTrailingZeros().scale() > 3) {
            throw new IllegalArgumentException(WEIGHT_SCALE);
        }
        return weight.setScale(3, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal validFixedPrice(BigDecimal fixedPrice) {
        if (fixedPrice == null) {
            return null;
        }
        if (fixedPrice.signum() <= 0) {
            throw new IllegalArgumentException(FIXED_PRICE_POSITIVE);
        }
        if (fixedPrice.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(FIXED_PRICE_SCALE);
        }
        return fixedPrice.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static ProductPresentation touch(ProductPresentation presentation, String username, Instant now) {
        presentation.setUpdatedBy(username);
        presentation.setUpdatedAt(now);
        return presentation;
    }

    private static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
