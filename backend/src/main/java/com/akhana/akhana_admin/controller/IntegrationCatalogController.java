package com.akhana.akhana_admin.controller;

import com.akhana.akhana_admin.config.IntegrationApiKeyFilter;
import com.akhana.akhana_admin.dto.IntegrationCatalogResponse;
import com.akhana.akhana_admin.service.IntegrationCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * INT-01: catalog for the WhatsApp bot, authenticated with CATALOG_API_KEY (IntegrationApiKeyFilter), never with a
 * user. nginx does not proxy /api/integrations, so it is reachable only inside the Docker network.
 */
@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IntegrationApiKeyFilter.AUTHORITY + "')")
public class IntegrationCatalogController {

    private final IntegrationCatalogService integrationCatalogService;

    @GetMapping("/catalog")
    public ResponseEntity<IntegrationCatalogResponse> getCatalog() {
        return ResponseEntity.ok(integrationCatalogService.getCatalog());
    }
}
