package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;

import java.util.List;
import java.util.UUID;

public interface CashSessionService {

    CashSessionSummaryResponse openSession(OpenCashRequest request, String username);

    CashSessionSummaryResponse getCurrentSession();

    SaleResponse registerSaleInCurrentSession(SaleRequest request, String username);

    CashSessionSummaryResponse closeCurrentSession(CloseCashRequest request, String username);

    List<CashSessionSummaryResponse> getAllSessions();

    CashSessionDetailResponse getSessionDetail(UUID id);
}
