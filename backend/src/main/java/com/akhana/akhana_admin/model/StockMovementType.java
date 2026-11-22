package com.akhana.akhana_admin.model;

/** INV-02: the kinds of rows of the kardex, with the words the screens use. */
public enum StockMovementType {
    INITIAL_LOAD("Carga inicial"),
    PURCHASE("Pedido entregado"),
    PURCHASE_REVERSAL("Pedido que sale del stock"),
    PURCHASE_CORRECTION("Corrección del pedido"),
    SALE("Venta"),
    AT_COST_SALE("Venta al costo"),
    CONSUMPTION("Consumo"),
    SALE_VOID("Venta anulada"),
    CONSUMPTION_VOID("Consumo anulado"),
    COUNT_ADJUSTMENT("Ajuste por conteo"),
    ADJUSTMENT("Ajuste"),
    AVERAGE_RESET("Ajuste del promedio");

    private final String label;

    StockMovementType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
