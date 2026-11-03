package com.akhana.akhana_admin.service;

import java.io.IOException;

// WEB-01, WEB-02, CAT-13
public interface WebCatalogExportService {

    /** Leaves the public copy up to date in its folder. If it fails, the previous copy stays as it was. */
    void export() throws IOException;
}
