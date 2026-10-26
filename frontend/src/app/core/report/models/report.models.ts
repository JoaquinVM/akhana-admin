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

// FIN-10 / CON-03: por proveedor actual del producto y mes, con el descuento general repartido por línea
export interface SalesBySupplierRow {
  /** Mes de la tienda (YYYY-MM); null en el total del proveedor y del rango */
  month: string | null;
  normalQuantity: number;
  normalTotal: number;
  normalCost: number;
  normalProfit: number;
  /** Utilidad ÷ total de las normales × 100; null si no hay ventas normales */
  normalMargin: number | null;
  atCostQuantity: number;
  atCostTotal: number;
  total: number;
}

export interface SalesBySupplier {
  /** null en «Sin proveedor» (líneas históricas sin proveedor) */
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

export interface ReportRange {
  from?: string | null;
  to?: string | null;
}
