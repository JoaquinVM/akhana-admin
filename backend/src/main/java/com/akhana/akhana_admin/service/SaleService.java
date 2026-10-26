package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SaleDetailRequest;
import com.akhana.akhana_admin.dto.SaleResponse;
import com.akhana.akhana_admin.dto.VoidSaleRequest;
import com.akhana.akhana_admin.model.PaymentMethod;
import com.akhana.akhana_admin.model.SaleStatus;
import com.akhana.akhana_admin.model.SaleType;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SaleService {

    SaleResponse registerSale(SaleDetailRequest request, String username);

    SaleResponse voidSale(UUID saleId, VoidSaleRequest request, String username);

    Page<SaleResponse> searchSales(LocalDate from, LocalDate to, SaleStatus status, SaleType saleType,
                                   PaymentMethod paymentMethod, String createdBy, int page, int size);

    List<String> getSellers();

    SaleResponse getSaleById(UUID saleId);

    List<SaleResponse> getSalesByCashSession(UUID cashSessionId);
}
