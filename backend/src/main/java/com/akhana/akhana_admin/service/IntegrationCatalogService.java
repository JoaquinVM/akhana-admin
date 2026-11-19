package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.IntegrationCatalogResponse;

/** INT-01: read-only catalog for the WhatsApp bot. */
public interface IntegrationCatalogService {

    IntegrationCatalogResponse getCatalog();
}
