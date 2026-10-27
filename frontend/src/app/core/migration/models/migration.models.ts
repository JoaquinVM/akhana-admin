// FIN-11: resultado de revisar o importar la migración histórica de las dos planillas
export interface MigrationSheetCount {
  sheet: string;
  read: number;
  toImport: number;
  rejected: number;
  afterCutoff: number;
}

// rowNumber es la fila de la planilla (el título es la fila 1)
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
  unassigned: number;
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
  unassignedLines: number;
  unassignedTotal: number;
  thirdPartyDebts: MigrationOwnerDebt[];
  months: MigrationMonthTotals[];
  movements: MigrationMovementTotal[];
}

export interface MigrationFiles {
  recaudaciones: File;
  pedidos: File;
  cutoffDate: string;
}
