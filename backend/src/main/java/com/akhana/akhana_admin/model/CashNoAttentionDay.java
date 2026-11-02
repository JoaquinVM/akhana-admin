package com.akhana.akhana_admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** CAJ-13: a day without a session, confirmed without attention by the opening of a later session. Never changes. */
@Entity
@Table(name = "cash_no_attention_days")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashNoAttentionDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "no_attention_date", nullable = false, updatable = false)
    private LocalDate noAttentionDate;

    @Column(nullable = false, length = 500, updatable = false)
    private String comment;

    @Column(name = "cash_session_id", nullable = false, updatable = false)
    private UUID cashSessionId;

    @Column(name = "marked_by", nullable = false, length = 100, updatable = false)
    private String markedBy;

    @Column(name = "marked_at", nullable = false, updatable = false)
    private Instant markedAt;
}
