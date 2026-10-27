package com.akhana.akhana_admin.model;

/** What a repayment to a person pays back; it sets the category of its expense. */
public enum CreditorRepaymentConcept {
    /** Purchases the person paid for the business: category Pedido (DEU-03, P-34). */
    PURCHASE,
    /** Sales of the person's products: category Pago a tercero (POS-13). */
    THIRD_PARTY
}
