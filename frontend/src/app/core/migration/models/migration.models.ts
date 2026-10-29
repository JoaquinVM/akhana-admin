// FIN-11: result of reviewing or importing the historical migration of both spreadsheets
export interface MigrationSheetCount {
  sheet: string;
  read: number;
  toImport: number;
  rejected: number;
  afterCutoff: number;
}

// rowNumber is the spreadsheet row (the header is row 1)
export interface MigrationRowMessage {
  sheet: string;
  rowNumber: number;
  messages: string[];
}

export interface MigrationOwnerDebt {
  creditorName: string;
  amount: number;
}

export interface MigrationMonthTotals {
  month: string;
  sales: number;
  profit: number;
  unidentified: number;
  atCost: number;
  thirdParty: number;
  consumptions: number;
}

export interface MigrationMovementTotal {
  month: string;
  concept: string;
  amount: number;
}

export interface MigrationResult {
  cutoffDate: string;
  imported: boolean;
  sheets: MigrationSheetCount[];
  rejected: MigrationRowMessage[];
  notices: MigrationRowMessage[];
  unidentifiedLines: number;
  unidentifiedTotal: number;
  thirdPartyDebts: MigrationOwnerDebt[];
  months: MigrationMonthTotals[];
  movements: MigrationMovementTotal[];
}

export interface MigrationFiles {
  recaudaciones: File;
  pedidos: File;
  cutoffDate: string;
}

// FIN-11: whether the one-time import was already done
export interface MigrationStatus {
  imported: boolean;
  cutoffDate: string | null;
  importedBy: string | null;
  importedAt: string | null;
}
