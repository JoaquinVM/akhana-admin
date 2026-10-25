package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.PaymentAccountResponse;
import com.akhana.akhana_admin.dto.PriceSuggestionResponse;
import com.akhana.akhana_admin.dto.PurchasePaymentRequest;
import com.akhana.akhana_admin.dto.PurchaseRequest;
import com.akhana.akhana_admin.dto.PurchaseResponse;
import com.akhana.akhana_admin.model.PurchasePaymentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PurchaseService {

    List<PurchaseResponse> getPurchases(UUID supplierId, PurchasePaymentStatus paymentStatus, String search);

    PurchaseResponse getPurchaseById(UUID id);

    PurchaseResponse registerPurchase(PurchaseRequest request, String username);

    PurchaseResponse registerPayment(UUID purchaseId, PurchasePaymentRequest request, String username);

    List<PaymentAccountResponse> getPaymentAccounts();

    PriceSuggestionResponse suggestPrice(UUID productId, BigDecimal newCost);
}
