// FIN-10 / CON-03: ventas normales y al costo en columnas aparte
export interface SalesByDayRow {
  /** Día de la tienda (YYYY-MM-DD); null en los subtotales del mes y el total del rango */
  date: string | null;
  normalSales: number;
  normalQuantity: number;
  normalTotal: number;
  normalCost: number;
  normalProfit: number;
  /** Utilidad ÷ total de las normales × 100; null si no hay ventas normales */
  normalMargin: number | null;
  atCostSales: number;
  atCostQuantity: number;
  atCostTotal: number;
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

export interface ReportRange {
  from?: string | null;
  to?: string | null;
}
