package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.PurchaseReadingSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a reading proposes for the purchase form (COM-20). It creates nothing: a person corrects and registers it.
 * A null {@code productId} or {@code unitCost} in a line must be resolved before registering.
 */
public record PurchaseDraftResponse(
    UUID readingId,
    PurchaseReadingSource source,
    UUID supplierId,
    String supplierText,
    // A loose note: everything must be reviewed
    boolean reviewAll,
    // The note says it is paid: the payment is proposed with no source (CA-19)
    boolean paidNote,
    List<Line> lines,
    BigDecimal shippingCost,
    BigDecimal readTotal,
    // Purchases of this supplier still waiting for delivery, to complete one instead of a new one (CA-12)
    List<PendingPurchase> pendingPurchases
) {
    public record Line(
        String text,
        BigDecimal quantity,
        boolean quantityDoubtful,
        String quantityText,
        UUID productId,
        List<ProductSuggestion> suggestions,
        BigDecimal unitCost,
        String costNote
    ) {}

    public record ProductSuggestion(UUID id, String code, String name, String supplierName) {}

    public record PendingPurchase(UUID id, String purchaseNumber, LocalDate purchaseDate, BigDecimal itemsTotal,
                                  boolean costToConfirm) {}
}
