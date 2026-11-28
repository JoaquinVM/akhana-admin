package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.model.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * INV-06, INV-18: the cost a line takes now. A product that controls its stock leaves at its weighted average, rounded
 * to the cent; one still to be counted, or that does not control stock, at its last cost, as in the V1.
 */
public final class StockCost {

    private StockCost() {
    }

    public static BigDecimal of(Product product) {
        if (product.controlsStockNow() && product.getStockAverageCost() != null) {
            return product.getStockAverageCost().setScale(2, RoundingMode.HALF_UP);
        }
        return product.getBuyPrice() != null
            ? product.getBuyPrice().setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
