package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.PayablesResponse;
import com.akhana.akhana_admin.dto.PaymentAccountResponse;
import com.akhana.akhana_admin.dto.PriceSuggestionResponse;
import com.akhana.akhana_admin.dto.PurchaseDeliveryRequest;
import com.akhana.akhana_admin.dto.PurchasePaymentRequest;
import com.akhana.akhana_admin.dto.PurchaseRequest;
import com.akhana.akhana_admin.dto.PurchaseResponse;
import com.akhana.akhana_admin.dto.VoidPurchaseRequest;
import com.akhana.akhana_admin.model.DeliveryStatus;
import com.akhana.akhana_admin.model.PurchasePaymentStatus;
import com.akhana.akhana_admin.model.PurchaseStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PurchaseService {

    List<PurchaseResponse> getPurchases(UUID supplierId, PurchaseStatus status, PurchasePaymentStatus paymentStatus,
                                        DeliveryStatus deliveryStatus, String search);

    PayablesResponse getPayables(UUID supplierId);

    PurchaseResponse getPurchaseById(UUID id);

    PurchaseResponse registerPurchase(PurchaseRequest request, String username);

    PurchaseResponse registerPayment(UUID purchaseId, PurchasePaymentRequest request, String username);

    PurchaseResponse updateDelivery(UUID purchaseId, PurchaseDeliveryRequest request, String username);

    PurchaseResponse voidPayment(UUID purchaseId, UUID paymentId, VoidPurchaseRequest request, String username);

    PurchaseResponse voidPurchase(UUID purchaseId, VoidPurchaseRequest request, String username);

    List<PaymentAccountResponse> getPaymentAccounts();

    PriceSuggestionResponse suggestPrice(UUID productId, BigDecimal newCost);
}
