// CAT-06: resultado de Revisar o Importar la hoja Productos
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
  // Solo las ya existentes, rechazadas y con aviso
  rows: ProductImportRow[];
}
