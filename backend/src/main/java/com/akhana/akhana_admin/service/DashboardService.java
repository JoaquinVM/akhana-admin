package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.DashboardResponse;

import java.time.LocalDate;

/** The dashboard (DSH-01 to DSH-04); it only reads. */
public interface DashboardService {

    DashboardResponse getDashboard(LocalDate from, LocalDate to);
}
