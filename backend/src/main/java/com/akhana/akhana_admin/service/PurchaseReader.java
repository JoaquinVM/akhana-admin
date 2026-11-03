package com.akhana.akhana_admin.service;

import java.math.BigDecimal;
import java.util.List;

/** COM-20: reads a supplier's text or photo with the AI. Never receives the catalog (COM-21). */
public interface PurchaseReader {

    boolean isConfigured();

    String model();

    ReadOutcome readText(String text);

    ReadOutcome readPhoto(byte[] jpeg);

    /** The purchase is null when the call failed; the tokens are null when the answer did not report them. */
    record ReadOutcome(ReadPurchase purchase, String rawResult, Long inputTokens, Long outputTokens) {
        public static ReadOutcome failed(Long inputTokens, Long outputTokens) {
            return new ReadOutcome(null, null, inputTokens, outputTokens);
        }
    }

    record ReadPurchase(String supplier, boolean multipleSuppliers, boolean looseNote, boolean paidNote,
                        List<ReadLine> lines, BigDecimal shipping, BigDecimal total) {}

    record ReadLine(String product, BigDecimal quantity, String quantityText, boolean quantityDoubtful,
                    BigDecimal unitCost, BigDecimal lineAmount, String note) {}
}
