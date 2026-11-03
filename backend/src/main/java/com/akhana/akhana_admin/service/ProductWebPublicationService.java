package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductResponse;

import java.util.UUID;

// CAT-13
public interface ProductWebPublicationService {

    ProductResponse mark(UUID productId, String username);

    ProductResponse unmark(UUID productId, String username);
}
