package com.akhana.akhana_admin.dto;

import java.math.BigDecimal;

/** COM-25: whether reading is on. The month's spend is only filled for an administrator (CA-25). */
public record PurchaseReadingStatusResponse(boolean configured, boolean capReached, BigDecimal spentUsd, BigDecimal capUsd) {}
