package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductImportResponse;

/** CAT-06: product import from the Productos sheet of the spreadsheet (.xlsx). Only creates, never updates. */
public interface ProductImportService {

    String INVALID_FILE = "El archivo debe ser un .xlsx de hasta 5 MB y 5.000 filas.";

    /** Validates every row and returns the result without saving anything. */
    ProductImportResponse preview(String fileName, byte[] content);

    /** Validates the same file again and saves the valid rows in one transaction. */
    ProductImportResponse importProducts(String fileName, byte[] content, String username);
}
