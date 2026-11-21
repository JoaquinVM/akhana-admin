package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;

/**
 * COM-10: the package a box becomes, as the person confirmed it: name, base units, own price (null: base quantity x
 * the base product price) and the box barcode it takes (null or blank: none). The rules are those of CAT-15.
 */
public record BoxConversionRequest(
    String name,
    BigDecimal baseQuantity,
    BigDecimal fixedPrice,
    String barcode
) {}
