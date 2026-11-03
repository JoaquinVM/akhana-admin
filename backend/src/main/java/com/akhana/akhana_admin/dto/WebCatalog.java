package com.akhana.akhana_admin.dto;

import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * WEB-02: everything the public site receives. A field added here becomes public, so these records never reuse
 * ProductResponse, which carries costs, profit, supplier and audit data.
 */
public record WebCatalog(List<Item> products) {

    public record Item(
        String code,
        String name,
        String category,
        String description,
        SaleUnit saleUnit,
        BigDecimal price,
        UUID photo,
        List<Presentation> presentations
    ) {}

    public record Presentation(String name, BigDecimal price) {}
}
