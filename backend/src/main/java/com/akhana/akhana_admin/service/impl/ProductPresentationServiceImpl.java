package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ProductPresentationRepository;
import com.akhana.akhana_admin.repository.ProductPriceChangeRepository;
import com.akhana.akhana_admin.service.ProductBarcodeService;
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
 * CAT-15: packages of a product, by unit or by kilo (the presentations of CAT-11 are the packages that are sold). The
 * product form sends the whole list; what is left out is deactivated, never deleted, because sales, consumptions and
 * purchases keep pointing to it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductPresentationServiceImpl implements ProductPresentationService {

    static final int MAX_ACTIVE = 6;
    static final String NAME_REQUIRED = "El nombre del empaque es obligatorio.";
    static final String NAME_TOO_LONG = "El nombre del empaque no debe superar los 50 caracteres.";
    static final String WEIGHT_POSITIVE = "El peso debe ser mayor a 0 kg.";
    static final String WEIGHT_SCALE = "El peso admite hasta 3 decimales (gramos).";
    static final String FIXED_PRICE_POSITIVE = "El precio propio debe ser mayor a 0.";
    static final String FIXED_PRICE_SCALE = "El precio propio admite hasta 2 decimales.";
    static final String TOO_MANY = "Un producto admite hasta " + MAX_ACTIVE + " empaques activos.";
    static final String NOT_OF_PRODUCT = "El empaque no pertenece a este producto.";

    // Warned when a sold package's price is below its cost, from the form or the import (CAT-02)
    static String lossWarning(String presentationName) {
        return "El empaque '" + presentationName + "' se vende a pérdida.";
    }

    static String wholeUnits(String presentationName) {
        return "La equivalencia de '" + presentationName + "' debe ser un número entero mayor a 1.";
    }

    static String notAvailable(ProductPresentation presentation, Product product) {
        return "El empaque '" + presentation.getName() + "' de '" + product.getName() + "' ya no está disponible.";
    }

    private final ProductPresentationRepository presentationRepository;
    private final ProductPriceChangeRepository priceChangeRepository;
    private final ProductBarcodeService barcodeService;

    @Override
    public List<String> applyPresentations(Product product, List<ProductPresentationRequest> requests,
                                           BigDecimal previousSellPrice, SaleUnit previousSaleUnit, String username) {
        Instant now = Instant.now();
        Set<UUID> fromOtherUnit = new HashSet<>();
        if (previousSaleUnit != null && previousSaleUnit != product.getSaleUnit()) {
            // The base quantity changes meaning (12 units are not 12 kg): every active package is deactivated
            product.getPresentations().stream().filter(ProductPresentation::isActive).forEach(p -> {
                fromOtherUnit.add(p.getId());
                deactivate(product, p, username, now);
            });
        }

        if (requests != null) {
            applyRequests(product, requests, fromOtherUnit, username, now);
        }
        product.getPresentations().sort(Comparator.comparing(ProductPresentation::getBaseQuantity));

        List<String> warnings = new ArrayList<>();
        List<ProductPresentation> sold = product.getPresentations().stream()
            .filter(p -> p.isActive() && p.isSold()).toList();
        for (ProductPresentation presentation : sold) {
            if (priceOf(presentation, product).compareTo(costOf(presentation, product)) < 0) {
                warnings.add(lossWarning(presentation.getName()));
            }
        }
        boolean priceChanged = previousSellPrice != null && previousSellPrice.compareTo(product.getSellPrice()) != 0;
        List<ProductPresentation> fixed = sold.stream().filter(p -> p.getFixedPrice() != null).toList();
        if (priceChanged && !fixed.isEmpty()) {
            warnings.add("Estos empaques tienen precio propio y no cambian: " + fixed.stream()
                .map(p -> p.getName() + " (" + StoreFormat.money(p.getFixedPrice()) + ")")
                .collect(Collectors.joining(", ")) + ".");
        }
        return warnings;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPresentation findSoldPresentation(Product product, UUID presentationId) {
        ProductPresentation presentation = presentationOf(product, presentationId);
        if (!presentation.isActive() || !presentation.isSold()) {
            throw new IllegalArgumentException(notAvailable(presentation, product));
        }
        return presentation;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPresentation findActivePresentation(Product product, UUID presentationId) {
        ProductPresentation presentation = presentationOf(product, presentationId);
        if (!presentation.isActive()) {
            throw new IllegalArgumentException(notAvailable(presentation, product));
        }
        return presentation;
    }

    /** Own price, or base quantity x product price (CAT-15). */
    public static BigDecimal priceOf(ProductPresentation presentation, Product product) {
        if (presentation.getFixedPrice() != null) {
            return presentation.getFixedPrice().setScale(2, RoundingMode.HALF_UP);
        }
        return presentation.getBaseQuantity().multiply(product.getSellPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    /** Always base quantity x product cost: a new cost from a purchase (COM-03) reaches every package. */
    public static BigDecimal costOf(ProductPresentation presentation, Product product) {
        return presentation.getBaseQuantity().multiply(product.getBuyPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    /** «12 u» or «0,227 kg»: how a base quantity reads in the messages. */
    public static String baseQuantityText(BigDecimal baseQuantity, SaleUnit saleUnit) {
        return saleUnit == SaleUnit.KILOGRAM
            ? StoreFormat.number(baseQuantity, 3) + " kg"
            : baseQuantity.setScale(0, RoundingMode.HALF_UP).toPlainString() + " u";
    }

    private ProductPresentation presentationOf(Product product, UUID presentationId) {
        return presentationRepository.findById(presentationId)
            .filter(p -> p.getProduct().getId().equals(product.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Empaque no encontrado con ID: " + presentationId));
    }

    private void applyRequests(Product product, List<ProductPresentationRequest> requests, Set<UUID> fromOtherUnit,
                               String username, Instant now) {
        Map<UUID, ProductPresentation> current = product.getPresentations().stream()
            .collect(Collectors.toMap(ProductPresentation::getId, p -> p));
        Set<UUID> requestedIds = new HashSet<>();
        List<ProductPresentation> finalActive = new ArrayList<>();
        Map<ProductPresentation, BigDecimal> previousFixedPrices = new HashMap<>();
        List<ProductPresentation> created = new ArrayList<>();
        Map<ProductPresentation, String> barcodes = new LinkedHashMap<>();

        for (ProductPresentationRequest request : requests) {
            boolean active = (request.active() == null || request.active())
                // A package of the unit the product just left stays off, even if the form still had it on
                && (request.id() == null || !fromOtherUnit.contains(request.id()));
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
                    deactivate(product, presentation, username, now);
                }
                continue;
            }

            String name = validName(request.name());
            BigDecimal baseQuantity = validBaseQuantity(request.baseQuantity(), name, product.getSaleUnit());
            boolean sold = request.sold() != null ? request.sold() : presentation.getId() == null || presentation.isSold();
            // A package that is not sold has no price
            BigDecimal fixedPrice = sold ? validFixedPrice(request.fixedPrice()) : null;
            boolean changed = presentation.getId() == null
                || !presentation.isActive()
                || !name.equals(presentation.getName())
                || baseQuantity.compareTo(presentation.getBaseQuantity()) != 0
                || sold != presentation.isSold()
                || !sameAmount(fixedPrice, presentation.getFixedPrice());
            if (presentation.getId() != null && !sameAmount(fixedPrice, presentation.getFixedPrice())) {
                previousFixedPrices.put(presentation, presentation.getFixedPrice());
            }
            if (presentation.getId() == null && fixedPrice != null) {
                previousFixedPrices.put(presentation, null);
            }
            presentation.setName(name);
            presentation.setBaseQuantity(baseQuantity);
            presentation.setFixedPrice(fixedPrice);
            presentation.setSold(sold);
            presentation.setActive(true);
            if (changed && presentation.getId() != null) {
                touch(presentation, username, now);
            }
            finalActive.add(presentation);
            if (request.barcode() != null) {
                barcodes.put(presentation, request.barcode());
            }
        }

        // Left out of the form: deactivated
        current.values().stream()
            .filter(p -> p.isActive() && !requestedIds.contains(p.getId()))
            .forEach(p -> deactivate(product, p, username, now));

        validateActiveSet(finalActive, product.getSaleUnit());

        // Deactivations reach the database before the inserts, so a re-created name or base quantity does not hit
        // the partial unique indexes (Hibernate flushes inserts before updates)
        presentationRepository.flush();
        for (ProductPresentation presentation : created) {
            product.getPresentations().add(presentationRepository.save(presentation));
        }
        // A barcode needs the saved package; the codes that are freed go first, so one can move to another package
        barcodes.entrySet().stream()
            .sorted(Comparator.comparing(entry -> !entry.getValue().isBlank()))
            .forEach(entry -> barcodeService.applyPresentationBarcode(product, entry.getKey(), entry.getValue(), username));
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

    private void deactivate(Product product, ProductPresentation presentation, String username, Instant now) {
        touch(presentation, username, now).setActive(false);
        // Its barcode is freed for another product or package (CAT-15)
        barcodeService.applyPresentationBarcode(product, presentation, "", username);
    }

    private static void validateActiveSet(List<ProductPresentation> active, SaleUnit saleUnit) {
        if (active.size() > MAX_ACTIVE) {
            throw new IllegalArgumentException(TOO_MANY);
        }
        Set<String> names = new HashSet<>();
        Set<BigDecimal> quantities = new HashSet<>();
        for (ProductPresentation presentation : active) {
            if (!names.add(presentation.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Ya existe el empaque '" + presentation.getName() + "' en este producto.");
            }
            if (!quantities.add(presentation.getBaseQuantity().setScale(3, RoundingMode.UNNECESSARY))) {
                throw new IllegalArgumentException("Ya existe un empaque de "
                    + baseQuantityText(presentation.getBaseQuantity(), saleUnit) + " en este producto.");
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

    // By kilo: kilos with up to 3 decimals, as the weight of CAT-11. By unit: a whole number of units, at least 2
    private static BigDecimal validBaseQuantity(BigDecimal quantity, String name, SaleUnit saleUnit) {
        if (saleUnit != SaleUnit.KILOGRAM) {
            if (quantity == null || quantity.stripTrailingZeros().scale() > 0 || quantity.compareTo(BigDecimal.TWO) < 0) {
                throw new IllegalArgumentException(wholeUnits(name));
            }
            return quantity.setScale(3, RoundingMode.UNNECESSARY);
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException(WEIGHT_POSITIVE);
        }
        if (quantity.stripTrailingZeros().scale() > 3) {
            throw new IllegalArgumentException(WEIGHT_SCALE);
        }
        return quantity.setScale(3, RoundingMode.UNNECESSARY);
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
}
