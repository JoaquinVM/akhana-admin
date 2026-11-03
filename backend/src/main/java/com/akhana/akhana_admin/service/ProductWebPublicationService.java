package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.dto.ProductWebPublicationChangeResponse;

import java.util.List;
import java.util.UUID;

// CAT-13
public interface ProductWebPublicationService {

    ProductResponse mark(UUID productId, String username);

    ProductResponse unmark(UUID productId, String username);

    /** Newest first. */
    List<ProductWebPublicationChangeResponse> history(UUID productId);
}
