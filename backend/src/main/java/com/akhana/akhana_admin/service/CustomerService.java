package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.*;
import com.akhana.akhana_admin.model.CustomerStatus;

import java.util.List;
import java.util.UUID;

/** Customers that buy at cost on credit, their receivable, history and collections (DEU-05 to DEU-07). */
public interface CustomerService {

    List<CustomerResponse> getCustomers(CustomerStatus status, String search);

    CustomerResponse getCustomerById(UUID id);

    CustomerResponse createCustomer(CustomerRequest request, String username);

    CustomerResponse updateCustomer(UUID id, CustomerRequest request, String username);

    void deleteCustomer(UUID id, String username);

    CustomerHistoryResponse getHistory(UUID id);

    CustomerHistoryResponse registerCollection(UUID customerId, CustomerCollectionRequest request, String username);

    CustomerHistoryResponse voidCollection(UUID customerId, UUID collectionId, VoidDebtRequest request, String username);
}
