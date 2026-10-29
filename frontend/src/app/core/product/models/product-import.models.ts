// CAT-06: result of Revisar or Importar of the Productos sheet
export type ProductImportRowStatus = 'IMPORTED' | 'EXISTING' | 'REJECTED' | 'WARNING';

export interface ProductImportRow {
  rowNumber: number;
  code: string;
  name: string;
  status: ProductImportRowStatus;
  messages: string[];
}

export interface ProductImportResult {
  totalRows: number;
  importedCount: number;
  presentationCount: number;
  existingCount: number;
  rejectedCount: number;
  warningCount: number;
  newCategories: string[];
  newSuppliers: string[];
  newTags: string[];
  // Only existing, rejected and warned rows
  rows: ProductImportRow[];
}
