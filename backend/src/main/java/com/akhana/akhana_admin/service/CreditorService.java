package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.CreditorStatus;

import java.util.List;
import java.util.UUID;

/** People the business owes money to, their debt, history and repayments (DEU-01, DEU-03, DEU-04). */
public interface CreditorService {

    List<CreditorResponse> getCreditors(CreditorStatus status, String search);

    CreditorResponse getCreditorById(UUID id);

    CreditorResponse createCreditor(CreditorRequest request, String username);

    CreditorResponse updateCreditor(UUID id, CreditorRequest request, String username);

    void deleteCreditor(UUID id, String username);

    CreditorHistoryResponse getHistory(UUID id);

    CreditorHistoryResponse registerRepayment(UUID creditorId, CreditorRepaymentRequest request, String username);

    CreditorHistoryResponse voidRepayment(UUID creditorId, UUID repaymentId, VoidDebtRequest request, String username);

    /** Active accounts to repay a person from, readable without Finanzas. */
    List<PaymentAccountResponse> getAccounts();
}
