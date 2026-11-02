package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.CashNoAttentionDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface CashNoAttentionDayRepository extends JpaRepository<CashNoAttentionDay, UUID> {

    List<CashNoAttentionDay> findByNoAttentionDateBetweenOrderByNoAttentionDateAsc(LocalDate from, LocalDate to);
}
