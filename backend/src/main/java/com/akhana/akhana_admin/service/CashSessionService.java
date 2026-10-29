package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashSessionService {

    CashSessionSummaryResponse openSession(OpenCashRequest request, String username);

    CashOpeningProposalResponse getOpeningProposal();

    CashSessionSummaryResponse getCurrentSession();

    /** The open session, or empty when the cash is closed (the POS asks without expecting an error). */
    Optional<CashSessionSummaryResponse> findCurrentSession();

    CashSessionSummaryResponse closeCurrentSession(CloseCashRequest request, String username);

    List<CashSessionSummaryResponse> getAllSessions();

    CashSessionDetailResponse getSessionDetail(UUID id);
}
