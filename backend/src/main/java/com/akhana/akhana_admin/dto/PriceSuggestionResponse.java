package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sell price that keeps the current percentage profit with a new cost (COM-03), rounded up to a whole Bs.
 * {@code suggestedSellPrice} is null when the current cost is 0 (no percentage to keep).
 */
public record PriceSuggestionResponse(
    UUID productId,
    BigDecimal currentCost,
    BigDecimal currentSellPrice,
    BigDecimal currentPercentageProfit,
    BigDecimal newCost,
    BigDecimal suggestedSellPrice
) {}
