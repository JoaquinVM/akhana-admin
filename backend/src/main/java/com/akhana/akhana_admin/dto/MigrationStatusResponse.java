package com.akhana.akhana_admin.dto;

import java.time.Instant;
import java.time.LocalDate;

/** FIN-11: whether the one-time historical import was done, and when and by whom. */
public record MigrationStatusResponse(
    boolean imported,
    LocalDate cutoffDate,
    String importedBy,
    Instant importedAt
) {}
