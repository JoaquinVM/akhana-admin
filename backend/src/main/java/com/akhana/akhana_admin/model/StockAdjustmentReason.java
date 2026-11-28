package com.akhana.akhana_admin.model;

/** INV-02: why a stock adjustment made by hand changes the stock. Only the surplus adds. */
public enum StockAdjustmentReason {
    MERMA("Merma", false),
    ROTURA("Rotura", false),
    VENCIMIENTO("Vencimiento", false),
    SOBRANTE("Sobrante", true);

    private final String label;
    private final boolean adds;

    StockAdjustmentReason(String label, boolean adds) {
        this.label = label;
        this.adds = adds;
    }

    public String label() {
        return label;
    }

    public boolean adds() {
        return adds;
    }
}
