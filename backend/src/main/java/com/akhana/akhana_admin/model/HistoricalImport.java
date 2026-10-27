package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** FIN-11: the one-time historical migration of the spreadsheets, with who ran it, when and up to which day. */
@Entity
@Table(name = "historical_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cutoff_date", nullable = false, updatable = false)
    private LocalDate cutoffDate;

    @Column(name = "imported_by", nullable = false, length = 100, updatable = false)
    private String importedBy;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;
}
