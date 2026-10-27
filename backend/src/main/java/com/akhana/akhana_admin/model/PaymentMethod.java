package com.akhana.akhana_admin.model;

public enum PaymentMethod {
    EFECTIVO,
    QR,
    MIXTO,
    // Whole at-cost sale on credit, nothing paid now (DEU-05)
    CREDITO,
    // Historical sale (FIN-11): no payments, its money is in the movements of Transacc.
    HISTORICO
}
