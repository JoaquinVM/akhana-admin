package com.akhana.akhana_admin.dto;

import java.util.List;

/**
 * CAT-06: result of reviewing or importing the product spreadsheet. Only the existing, rejected and warned rows are
 * listed; the imported rows without notices are only counted.
 */
public record ProductImportResponse(
    int totalRows,
    int importedCount,
    int presentationCount,
    int existingCount,
    int rejectedCount,
    int warningCount,
    List<String> newCategories,
    List<String> newSuppliers,
    List<String> newTags,
    List<Row> rows
) {
    public enum RowStatus { IMPORTED, EXISTING, REJECTED, WARNING }

    /** rowNumber is the spreadsheet row (the header is row 1). */
    public record Row(int rowNumber, String code, String name, RowStatus status, List<String> messages) {}
}
