package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.dto.PaymentAccountResponse;
import com.akhana.akhana_admin.service.CreditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Shared reads of the Deudas screen: the operator does not open Finanzas. */
@RestController
@PreAuthorize("hasAuthority('DEBTS')")
@RequestMapping("/api/debts")
@RequiredArgsConstructor
public class DebtController {

    private final CreditorService creditorService;

    @GetMapping("/accounts")
    public ResponseEntity<List<PaymentAccountResponse>> getAccounts() {
        return ResponseEntity.ok(creditorService.getAccounts());
    }
}
