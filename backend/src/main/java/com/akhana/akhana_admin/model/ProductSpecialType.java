package com.akhana.akhana_admin.model;

/**
 * Special products sold in the POS with a typed name and price (POS-11). They have no cost, price or stock in the
 * catalog. Normal products have no special type.
 */
public enum ProductSpecialType {
    /** "Producto sin identificar": its lines are assigned to the real product later (POS-12). */
    UNIDENTIFIED,
    /** Sold on behalf of its owner (a creditor): cost = price and every sale is owed to them (POS-13). */
    THIRD_PARTY
}
