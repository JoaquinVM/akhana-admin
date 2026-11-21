package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.BoxConversionRequest;
import com.akhana.akhana_admin.dto.BoxProposalResponse;
import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.BoxProductReviewRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.BoxProductConversionService;
import com.akhana.akhana_admin.service.ProductBarcodeService;
import com.akhana.akhana_admin.service.ProductPresentationService;
import com.akhana.akhana_admin.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * COM-10: a box that the V1 sells as its own product (Barra Chia Caja x12, NSE-BAR-CHI-CAJ) becomes the package Caja
 * ×12 of its base product (Barra Chia, NSE-BAR-CHI). The system only proposes; a person confirms or discards each one.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BoxProductConversionServiceImpl implements BoxProductConversionService {

    static final String ALREADY_REVIEWED = "Esta caja ya se revisó.";
    static final String BARCODE_NOT_OF_BOX = "El código no es de la caja.";

    // A box word and a count of units: «caja x12», «Display x 24», «pack ×6»; not «x 300 gr»
    private static final Pattern BOX_WORD = Pattern.compile("\\b(caja|cja|display|pack|paquete|fardo)\\b");
    private static final Pattern COUNT = Pattern.compile(
        "(?:\\bx|×)\\s*(\\d{1,4})\\b(?!\\s*(?:g|gr|grs|gramos|kg|ml|cc|l|lt|lts|cap|caps|capsulas)\\b)");
    // The end of a box code over its base code: NSE-BAR-CHI-CAJ
    private static final Pattern BOX_CODE = Pattern.compile("^(.+)-(?:caj|cja|dis|pac|paq)\\d*$");
    private static final Map<String, String> PACKAGE_WORDS = Map.of(
        "caja", "Caja", "cja", "Caja", "display", "Display", "pack", "Pack", "paquete", "Paquete", "fardo", "Fardo");

    private final ProductRepository productRepository;
    private final BoxProductReviewRepository reviewRepository;
    private final ProductService productService;
    private final ProductPresentationService presentationService;
    private final ProductBarcodeService barcodeService;
    // The store's clock (America/La_Paz)
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<BoxProposalResponse> findProposals() {
        List<Product> candidates = candidates();
        Set<UUID> reviewed = reviewRepository.findReviewedBoxProductIds();
        return candidates.stream()
            .filter(box -> !reviewed.contains(box.getId()))
            .map(box -> proposalFor(box, candidates))
            .flatMap(Optional::stream)
            .toList();
    }

    @Override
    public ProductResponse convert(UUID boxProductId, BoxConversionRequest request, String username) {
        Product box = productRepository.findByIdWithRelations(boxProductId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + boxProductId));
        if (reviewRepository.existsByBoxProductId(boxProductId)) {
            throw new IllegalStateException(ALREADY_REVIEWED);
        }
        // The base comes from the server's own proposal, never from the client
        BoxProposalResponse proposal = proposalFor(box, candidates())
            .orElseThrow(() -> new IllegalArgumentException("'" + box.getName() + "' ya no se propone como caja."));
        Product base = productRepository.findByIdWithRelations(proposal.baseProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + proposal.baseProductId()));
        String barcode = request.barcode() == null || request.barcode().isBlank() ? null : request.barcode().trim();
        if (barcode != null && !proposal.boxBarcodes().contains(BarcodeFormat.canonicalize(barcode).orElse(barcode))) {
            throw new IllegalArgumentException(BARCODE_NOT_OF_BOX);
        }

        // 1. The package, with the rules of CAT-15: a package that cannot be created leaves the box as it was
        List<ProductPresentationRequest> requests = new ArrayList<>();
        base.getPresentations().forEach(p -> requests.add(new ProductPresentationRequest(
            p.getId(), p.getName(), p.getBaseQuantity(), p.getFixedPrice(), p.isActive(), p.isSold(), null)));
        requests.add(new ProductPresentationRequest(null, request.name(), request.baseQuantity(), request.fixedPrice(),
            true, true, null));
        List<String> warnings = presentationService.applyPresentations(base, requests, null, null, username);
        String name = request.name().trim();
        ProductPresentation created = base.getPresentations().stream()
            .filter(p -> p.isActive() && p.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow();

        // 2. The box is deleted (RG-01, CAT-03), which frees its barcodes; 3. the package takes the chosen one
        productService.deleteProduct(box.getId(), username);
        productRepository.flush();
        if (barcode != null) {
            barcodeService.applyPresentationBarcode(base, created, barcode, username);
        }
        Instant now = Instant.now(clock);
        base.setUpdatedBy(username);
        base.setUpdatedAt(now);
        Product saved = productRepository.save(base);
        reviewRepository.save(BoxProductReview.builder()
            .boxProductId(box.getId())
            .decision(BoxProductDecision.CONVERTED)
            .baseProductId(saved.getId())
            .presentationId(created.getId())
            .decidedBy(username)
            .decidedAt(now)
            .build());
        return ProductResponse.fromEntity(saved, warnings);
    }

    @Override
    public void discard(UUID boxProductId, String username) {
        Product box = productRepository.findById(boxProductId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + boxProductId));
        if (reviewRepository.existsByBoxProductId(boxProductId)) {
            throw new IllegalStateException(ALREADY_REVIEWED);
        }
        reviewRepository.save(BoxProductReview.builder()
            .boxProductId(box.getId())
            .decision(BoxProductDecision.DISCARDED)
            .decidedBy(username)
            .decidedAt(Instant.now(clock))
            .build());
    }

    // Active products by unit that are not special: the boxes and their base products are among them
    private List<Product> candidates() {
        return productRepository.findByStatusOrderByNameAsc(ProductStatus.ACTIVO).stream()
            .filter(p -> !p.isSpecial() && p.getSaleUnit() == SaleUnit.UNIT)
            .toList();
    }

    static Optional<BoxProposalResponse> proposalFor(Product box, List<Product> products) {
        String name = normalize(box.getName());
        Matcher word = BOX_WORD.matcher(name);
        Matcher count = COUNT.matcher(name);
        if (!word.find() || !count.find()) {
            return Optional.empty();
        }
        int units = Integer.parseInt(count.group(1));
        if (units < 2) {
            return Optional.empty();
        }
        List<Product> sameSupplier = products.stream()
            .filter(p -> !p.getId().equals(box.getId()) && Objects.equals(supplierId(p), supplierId(box)))
            .toList();
        Optional<Product> base = baseByCode(box, sameSupplier).or(() -> baseByName(name, word, count, sameSupplier));
        return base.map(b -> new BoxProposalResponse(
            box.getId(),
            box.getCode(),
            box.getName(),
            box.getSellPrice(),
            box.getBuyPrice(),
            box.getBarcodes().stream().filter(c -> c.isActive() && c.getPresentationId() == null)
                .map(ProductBarcode::getBarcode).toList(),
            box.getSupplier() != null ? box.getSupplier().getName() : null,
            b.getId(),
            b.getCode(),
            b.getName(),
            b.getSellPrice(),
            b.getBuyPrice(),
            PACKAGE_WORDS.get(word.group(1)) + " ×" + units,
            BigDecimal.valueOf(units),
            box.getSellPrice()));
    }

    private static Optional<Product> baseByCode(Product box, List<Product> products) {
        Matcher code = BOX_CODE.matcher(box.getCode().toLowerCase(Locale.ROOT));
        if (!code.matches()) {
            return Optional.empty();
        }
        String baseCode = code.group(1);
        return products.stream().filter(p -> p.getCode().toLowerCase(Locale.ROOT).equals(baseCode)).findFirst();
    }

    // The box name without the box word and the count: «barra chia caja x12» is «barra chia»
    private static Optional<Product> baseByName(String name, Matcher word, Matcher count, List<Product> products) {
        String stripped = (name.substring(0, Math.min(word.start(), count.start()))
            + " " + name.substring(Math.min(word.end(), count.end()), Math.max(word.start(), count.start()))
            + " " + name.substring(Math.max(word.end(), count.end())))
            .replaceAll("\\b(de|por|con)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (stripped.isEmpty()) {
            return Optional.empty();
        }
        List<Product> matches = products.stream().filter(p -> normalize(p.getName()).equals(stripped)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static UUID supplierId(Product product) {
        return product.getSupplier() != null ? product.getSupplier().getId() : null;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
