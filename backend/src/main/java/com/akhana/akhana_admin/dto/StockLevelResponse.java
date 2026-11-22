package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** INV-11: the stock of a product that controls it, for «Quedan N» in the POS. */
public record StockLevelResponse(UUID productId, BigDecimal quantity) {
}
