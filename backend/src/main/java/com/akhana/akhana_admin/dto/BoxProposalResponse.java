package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * COM-10: a product that looks like a box of another one, with the package it would become. The box's barcodes are
 * the ones it can hand to the package (the first one by default).
 */
public record BoxProposalResponse(
    UUID boxProductId,
    String boxCode,
    String boxName,
    BigDecimal boxSellPrice,
    BigDecimal boxBuyPrice,
    List<String> boxBarcodes,
    String supplierName,
    UUID baseProductId,
    String baseCode,
    String baseName,
    BigDecimal baseSellPrice,
    BigDecimal baseBuyPrice,
    String proposedName,
    BigDecimal proposedBaseQuantity,
    BigDecimal proposedPrice
) {}
