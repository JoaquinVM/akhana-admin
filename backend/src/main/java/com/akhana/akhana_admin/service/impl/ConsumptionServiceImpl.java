package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ConsumptionItemRequest;
import com.akhana.akhana_admin.dto.ConsumptionRequest;
import com.akhana.akhana_admin.dto.ConsumptionResponse;
import com.akhana.akhana_admin.dto.VoidConsumptionRequest;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.*;
import com.akhana.akhana_admin.repository.ConsumptionRepository;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.service.ConsumptionService;
import com.akhana.akhana_admin.service.ProductPresentationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CON-01: internal consumptions valued at the product cost of the moment. They move no money, so they touch no
 * cash session, account or financial movement.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConsumptionServiceImpl implements ConsumptionService {

    static final String NO_ITEMS = "El consumo debe tener al menos una línea.";
    static final String FUTURE_DATE = "La fecha del consumo no puede ser futura.";
    static final String ALREADY_VOIDED = "El consumo ya está anulado.";
    static final String VOID_REASON_REQUIRED = "El motivo de anulación es obligatorio.";

    private final ConsumptionRepository consumptionRepository;
    private final ProductRepository productRepository;
    private final ProductPresentationService presentationService;

    @Override
    @Transactional(readOnly = true)
    public List<ConsumptionResponse> getConsumptions(LocalDate from, LocalDate to, UUID productId,
                                                     ConsumptionStatus status, String search) {
        String term = search != null && !search.isBlank() ? search.trim() : null;
        return consumptionRepository.search(from, to, status, productId, term).stream()
            .map(ConsumptionResponse::summary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumptionResponse getConsumptionById(UUID id) {
        return ConsumptionResponse.detail(findConsumption(id));
    }

    @Override
    public ConsumptionResponse registerConsumption(ConsumptionRequest request, String username) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException(NO_ITEMS);
        }
        LocalDate today = LocalDate.now(PurchaseServiceImpl.STORE_ZONE);
        LocalDate consumptionDate = request.consumptionDate() != null ? request.consumptionDate() : today;
        if (consumptionDate.isAfter(today)) {
            throw new IllegalArgumentException(FUTURE_DATE);
        }

        // The same product (and presentation, CAT-11) twice adds up in one line, like the POS (POS-01)
        Map<LineKey, BigDecimal> quantities = new LinkedHashMap<>();
        Map<UUID, Product> products = new LinkedHashMap<>();
        Map<UUID, ProductPresentation> presentations = new LinkedHashMap<>();
        for (ConsumptionItemRequest itemReq : request.items()) {
            Product product = products.containsKey(itemReq.productId())
                ? products.get(itemReq.productId())
                : findConsumableProduct(itemReq.productId());
            products.put(product.getId(), product);
            BigDecimal quantity;
            if (itemReq.presentationId() != null) {
                ProductPresentation presentation = presentationService.findActivePresentation(product, itemReq.presentationId());
                presentations.put(presentation.getId(), presentation);
                quantity = SaleServiceImpl.normalizePackQuantity(itemReq.quantity(), product.getName(), presentation.getName());
            } else {
                quantity = SaleServiceImpl.normalizeQuantity(itemReq.quantity(), product.getName(), product.getSaleUnit());
            }
            quantities.merge(new LineKey(product.getId(), itemReq.presentationId()), quantity, BigDecimal::add);
        }

        Instant now = Instant.now();
        Consumption consumption = Consumption.builder()
            .consumptionNumber(formatConsumptionNumber(consumptionRepository.nextConsumptionNumber()))
            .consumptionDate(consumptionDate)
            .consumedFor(trimToNull(request.consumedFor()))
            .comment(trimToNull(request.comment()))
            .createdBy(username)
            .createdAt(now)
            .build();

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Map.Entry<LineKey, BigDecimal> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey().productId());
            ProductPresentation presentation = entry.getKey().presentationId() != null
                ? presentations.get(entry.getKey().presentationId())
                : null;
            BigDecimal quantity = entry.getValue().setScale(3, RoundingMode.HALF_UP);
            // Cost snapshot: later cost changes (COM-03 or catalog) do not alter this consumption
            BigDecimal unitCost = product.getBuyPrice().setScale(2, RoundingMode.HALF_UP);
            // By presentation the cost per kilo applies to the kilos of the packs (CAT-11)
            BigDecimal weightKg = presentation != null ? SaleServiceImpl.packWeight(quantity, presentation) : null;
            BigDecimal subtotal = unitCost.multiply(weightKg != null ? weightKg : quantity).setScale(2, RoundingMode.HALF_UP);
            consumption.getItems().add(ConsumptionItem.builder()
                .consumption(consumption)
                .product(product)
                .productName(product.getName())
                .productCode(product.getCode())
                .quantity(quantity)
                .presentation(presentation)
                .presentationName(presentation != null ? presentation.getName() : null)
                .presentationWeightKg(presentation != null ? presentation.getWeightKg() : null)
                .weightKg(weightKg)
                .unitCost(unitCost)
                .subtotal(subtotal)
                .build());
            total = total.add(subtotal);
        }
        consumption.setTotalAmount(total);

        return ConsumptionResponse.detail(consumptionRepository.save(consumption));
    }

    @Override
    public ConsumptionResponse voidConsumption(UUID id, VoidConsumptionRequest request, String username) {
        String voidReason = requireVoidReason(request);
        Consumption consumption = consumptionRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Consumo no encontrado con ID: " + id));
        if (consumption.getStatus() == ConsumptionStatus.ANULADO) {
            throw new IllegalStateException(ALREADY_VOIDED);
        }
        // No money to revert, so it does not depend on the cash session
        consumption.setStatus(ConsumptionStatus.ANULADO);
        consumption.setVoidedBy(username);
        consumption.setVoidedAt(Instant.now());
        consumption.setVoidReason(voidReason);
        return ConsumptionResponse.detail(consumptionRepository.save(consumption));
    }

    private record LineKey(UUID productId, UUID presentationId) {}

    static String formatConsumptionNumber(long sequenceValue) {
        return String.format("CON-%06d", sequenceValue);
    }

    // An inactive product is accepted: it may still be in the store although it is no longer sold
    private Product findConsumableProduct(UUID productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
        if (product.getStatus() == ProductStatus.ELIMINADO) {
            throw new IllegalArgumentException("El producto '" + product.getName() + "' no existe o fue eliminado.");
        }
        ProductServiceImpl.ensureNotSpecial(product);
        return product;
    }

    private Consumption findConsumption(UUID id) {
        return consumptionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Consumo no encontrado con ID: " + id));
    }

    // Every void needs a reason (RG-03), also when the service is called without bean validation
    private static String requireVoidReason(VoidConsumptionRequest request) {
        if (request == null || request.voidReason() == null || request.voidReason().isBlank()) {
            throw new IllegalArgumentException(VOID_REASON_REQUIRED);
        }
        return request.voidReason().trim();
    }

    private static String trimToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
