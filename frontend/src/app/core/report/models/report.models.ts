// FIN-10 / CON-03: normal and at-cost sales in separate columns
export interface SalesByDayRow {
  /** Store day (YYYY-MM-DD); null in the month subtotals and the range total */
  date: string | null;
  normalSales: number;
  normalQuantity: number;
  normalTotal: number;
  normalCost: number;
  normalProfit: number;
  /** Profit ÷ normal total × 100; null without normal sales */
  normalMargin: number | null;
  atCostSales: number;
  atCostQuantity: number;
  atCostTotal: number;
  /** POS-12: lines pending assignment; store sales left out of profit until assigned */
  pendingTotal: number;
  /** POS-12: lines closed as unidentified; store sales always left out of profit */
  unidentifiedTotal: number;
  /** POS-13: third-party sales; they add neither to the store's total nor to its profit */
  thirdPartyTotal: number;
  /** Normal + at cost + pending assignment + unidentified */
  total: number;
  profit: number;
}

export interface SalesByDayMonth {
  /** YYYY-MM */
  month: string;
  days: SalesByDayRow[];
  totals: SalesByDayRow;
}

export interface SalesByDayReport {
  from: string;
  to: string;
  months: SalesByDayMonth[];
  totals: SalesByDayRow;
}

// FIN-10 / CON-03: by the product's current supplier and month, with the general discount spread per line
export interface SalesBySupplierRow {
  /** Store month (YYYY-MM); null in the supplier and range totals */
  month: string | null;
  normalQuantity: number;
  normalTotal: number;
  normalCost: number;
  normalProfit: number;
  /** Profit ÷ normal total × 100; null without normal sales */
  normalMargin: number | null;
  atCostQuantity: number;
  atCostTotal: number;
  total: number;
}

export interface SalesBySupplier {
  /** null in «Sin proveedor» (historical lines without a supplier) */
  supplierId: string | null;
  supplierName: string;
  months: SalesBySupplierRow[];
  totals: SalesBySupplierRow;
}

export interface SalesBySupplierReport {
  from: string;
  to: string;
  suppliers: SalesBySupplier[];
  totals: SalesBySupplierRow;
}

export interface CategoryAmount {
  /** null in «Sin categoría» */
  categoryId: string | null;
  categoryName: string;
  /** Ingresos en positivo, gastos en negativo */
  amount: number;
}

export interface TransferAmount {
  sourceAccountId: string;
  sourceAccountName: string;
  destinationAccountId: string;
  destinationAccountName: string;
  amount: number;
}

export interface TransactionsByCategoryMonth {
  /** Store month (YYYY-MM); null in the range total */
  month: string | null;
  incomes: CategoryAmount[];
  incomeTotal: number;
  expenses: CategoryAmount[];
  expenseTotal: number;
  /** Ingresos + gastos; no incluye transferencias ni consumos */
  net: number;
  transfers: TransferAmount[];
  consumptions: number;
  /** Consumptions at cost, positive; no money movement (CON-03) */
  consumptionTotal: number;
}

export interface TransactionsByCategoryReport {
  from: string;
  to: string;
  months: TransactionsByCategoryMonth[];
  totals: TransactionsByCategoryMonth;
}

// FIN-10 / CON-03: totals per month; sales are the month subtotals of sales by day and consumptions go apart
export interface TotalsRow {
  /** Store month (YYYY-MM); null in the range total */
  month: string | null;
  normalSales: number;
  normalTotal: number;
  normalCost: number;
  normalProfit: number;
  /** Profit ÷ normal total × 100; null without normal sales */
  normalMargin: number | null;
  atCostSales: number;
  atCostTotal: number;
  /** POS-12, POS-13: same as in sales by day */
  pendingTotal: number;
  unidentifiedTotal: number;
  thirdPartyTotal: number;
  total: number;
  consumptions: number;
  /** Consumptions at cost, no money movement */
  consumptionTotal: number;
  /** Profit of the normal sales − consumptions at cost */
  profitAfterConsumptions: number;
}

export interface TotalsReport {
  from: string;
  to: string;
  months: TotalsRow[];
  totals: TotalsRow;
}

export interface ConsumptionsDayRow {
  /** Store day (YYYY-MM-DD); null in the month subtotal and the range total */
  date: string | null;
  consumptions: number;
  /** Sum of its lines' quantities (kilos for products by weight) */
  quantity: number;
  /** Total at cost, no money movement */
  total: number;
}

export interface ConsumptionsMonth {
  month: string;
  days: ConsumptionsDayRow[];
  totals: ConsumptionsDayRow;
}

export interface ConsumptionsSupplierRow {
  /** Store month (YYYY-MM); null in the supplier and range totals */
  month: string | null;
  quantity: number;
  total: number;
}

export interface ConsumptionsSupplier {
  /** null en «Sin proveedor» */
  supplierId: string | null;
  supplierName: string;
  months: ConsumptionsSupplierRow[];
  totals: ConsumptionsSupplierRow;
}

export interface ConsumptionsReport {
  from: string;
  to: string;
  months: ConsumptionsMonth[];
  totals: ConsumptionsDayRow;
  suppliers: ConsumptionsSupplier[];
  supplierTotals: ConsumptionsSupplierRow;
}

export interface ReportRange {
  from?: string | null;
  to?: string | null;
}

/** Report exportable to Excel: its path segment in /api/reports (FIN-10) */
export type ReportName = 'sales-by-day' | 'sales-by-supplier' | 'transactions-by-category' | 'totals' | 'consumptions';

/** Archivo .xlsx descargado: akhana-<reporte>-<desde>-<hasta>.xlsx */
export interface ReportFile {
  fileName: string;
  content: Blob;
}
