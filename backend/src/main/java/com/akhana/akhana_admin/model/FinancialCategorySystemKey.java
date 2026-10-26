package com.akhana.akhana_admin.model;

/** Expense categories the code needs (FIN-05): resolved by key, so renaming them does not break purchases. */
public enum FinancialCategorySystemKey {
    /** Pedido: payments of a purchase (COM-05, COM-06). */
    PURCHASE_PAYMENT,
    /** Transporte: shipping of a purchase (P-24). */
    PURCHASE_SHIPPING,
    /** Ajuste (income): surplus recorded when closing the cash session (CAJ-09). */
    CASH_SURPLUS,
    /** Ajuste (expense): shortage recorded when closing the cash session (CAJ-09). */
    CASH_SHORTAGE
}
