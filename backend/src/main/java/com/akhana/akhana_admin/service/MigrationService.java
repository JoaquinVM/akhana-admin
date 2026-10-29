package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.MigrationResponse;
import com.akhana.akhana_admin.dto.MigrationStatusResponse;

import java.time.LocalDate;

/** FIN-11: one-time historical migration of Akhana Recaudaciones and Akhana Pedidos (specs/migration.md). */
public interface MigrationService {

    String ALREADY_DONE = "La migración histórica ya se hizo.";
    String HAS_REJECTED = "Hay filas rechazadas: se corrigen en la planilla y se revisa de nuevo. No se importó nada.";

    /** Whether the import was already done, so the screen says it before any file is chosen. */
    MigrationStatusResponse status();

    /** Reads and validates both files without saving. */
    MigrationResponse preview(String recaudacionesName, byte[] recaudaciones, String pedidosName, byte[] pedidos,
                              LocalDate cutoffDate);

    /** Validates again and saves everything in one transaction, only when no row is rejected. */
    MigrationResponse importHistory(String recaudacionesName, byte[] recaudaciones, String pedidosName, byte[] pedidos,
                                    LocalDate cutoffDate, String username);
}
