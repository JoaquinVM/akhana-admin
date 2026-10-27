package com.akhana.akhana_admin.model;

/** State of an unidentified line (POS-12); null for every other line. */
public enum SaleItemAssignmentStatus {
    /** Cost pending: reports keep it out of the profit. */
    POR_ASIGNAR,
    /** Took the real product and its cost at the moment of the assignment. */
    ASIGNADO,
    /** Will never be identified: cost 0. */
    SIN_IDENTIFICAR
}
