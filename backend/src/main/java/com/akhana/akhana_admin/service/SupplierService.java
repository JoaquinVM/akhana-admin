package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SupplierRequest;
import com.akhana.akhana_admin.dto.SupplierResponse;

import java.util.List;
import java.util.UUID;

public interface SupplierService {

    List<SupplierResponse> getAllSuppliers(String search);

    List<SupplierResponse> getAllSuppliers(String search, String status);

    SupplierResponse getSupplierById(UUID id);

    SupplierResponse createSupplier(SupplierRequest request, String currentUsername);

    SupplierResponse updateSupplier(UUID id, SupplierRequest request, String currentUsername);

    void deleteSupplier(UUID id, String currentUsername);
}
