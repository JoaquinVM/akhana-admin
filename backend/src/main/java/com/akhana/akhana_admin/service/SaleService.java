package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;

import java.util.List;
import java.util.UUID;

public interface SaleService {

    SaleResponse registerSale(SaleDetailRequest request, String username);

    SaleResponse voidSale(UUID saleId, VoidSaleRequest request, String username);

    List<SaleResponse> getAllSales();

    SaleResponse getSaleById(UUID saleId);

    List<SaleResponse> getSalesByCashSession(UUID cashSessionId);
}
