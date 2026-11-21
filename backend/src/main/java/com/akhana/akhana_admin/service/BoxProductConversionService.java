package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.BoxConversionRequest;
import com.akhana.akhana_admin.dto.BoxProposalResponse;
import com.akhana.akhana_admin.dto.ProductResponse;

import java.util.List;
import java.util.UUID;

/** COM-10: the boxes that are products of their own in the V1 become packages of their base product (CAT-15). */
public interface BoxProductConversionService {

    List<BoxProposalResponse> findProposals();

    /** Creates the package in the base product and deletes the box; returns the base product. */
    ProductResponse convert(UUID boxProductId, BoxConversionRequest request, String username);

    void discard(UUID boxProductId, String username);
}
