package com.akhana.akhana_admin.model;

/** Expense categories the code needs (FIN-05): resolved by key, so renaming them does not break purchases. */
public enum FinancialCategorySystemKey {
    /** Pedido: payments of a purchase (COM-05, COM-06). */
    PURCHASE_PAYMENT,
    /** Transporte: shipping of a purchase (P-24). */
    PURCHASE_SHIPPING
}
