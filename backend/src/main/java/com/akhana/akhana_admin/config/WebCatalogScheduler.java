package com.akhana.akhana_admin.config;

import com.akhana.akhana_admin.service.WebCatalogExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// CAT-13: every 5 minutes, and the host uploads every 10, so a change is online in less than 15 (specs/web-catalog.md).
// On only in production. A failure leaves the previous copy, and the upload alerts when the copy gets old.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.web-catalog.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class WebCatalogScheduler {

    private final WebCatalogExportService exportService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void export() {
        try {
            exportService.export();
        } catch (Exception ex) {
            log.error("Catálogo web: no se pudo exportar", ex);
        }
    }
}
