package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.StockCountLineRequest;
import com.akhana.akhana_admin.dto.StockCountRequest;
import com.akhana.akhana_admin.dto.StockCountResponse;
import com.akhana.akhana_admin.dto.StockCountReviewResponse;

import java.util.List;
import java.util.UUID;

/** INV-05, INV-18: counts by category or product, blind, and their confirmation. */
public interface StockCountService {

    StockCountResponse create(StockCountRequest request, String username);

    List<StockCountResponse> list();

    StockCountResponse get(UUID countId);

    StockCountResponse saveLine(UUID countId, UUID productId, StockCountLineRequest request, String username);

    StockCountReviewResponse review(UUID countId);

    StockCountResponse confirm(UUID countId, String username);

    StockCountResponse discard(UUID countId, String username);
}
