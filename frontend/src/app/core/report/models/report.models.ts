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

export interface CategoryAmount {
  /** null en «Sin categoría» */
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
  /** Mes de la tienda (YYYY-MM); null en el total del rango */
  month: string | null;
  incomes: CategoryAmount[];
  incomeTotal: number;
  expenses: CategoryAmount[];
  expenseTotal: number;
  /** Ingresos + gastos; no incluye transferencias ni consumos */
  net: number;
  transfers: TransferAmount[];
  consumptions: number;
  /** Consumos al costo, en positivo; sin movimiento de dinero (CON-03) */
  consumptionTotal: number;
}

export interface TransactionsByCategoryReport {
  from: string;
  to: string;
  months: TransactionsByCategoryMonth[];
  totals: TransactionsByCategoryMonth;
}

// FIN-10 / CON-03: totales por mes; las ventas son los subtotales de ventas por día y los consumos van aparte
export interface TotalsRow {
  /** Mes de la tienda (YYYY-MM); null en el total del rango */
  month: string | null;
  normalSales: number;
  normalTotal: number;
  normalCost: number;
  normalProfit: number;
  /** Utilidad ÷ total de las normales × 100; null si no hay ventas normales */
  normalMargin: number | null;
  atCostSales: number;
  atCostTotal: number;
  total: number;
  consumptions: number;
  /** Consumos al costo, sin movimiento de dinero */
  consumptionTotal: number;
  /** Utilidad de las ventas normales − consumos al costo */
  profitAfterConsumptions: number;
}

export interface TotalsReport {
  from: string;
  to: string;
  months: TotalsRow[];
  totals: TotalsRow;
}

export interface ConsumptionsDayRow {
  /** Día de la tienda (YYYY-MM-DD); null en el subtotal del mes y en el total del rango */
  date: string | null;
  consumptions: number;
  /** Suma de las cantidades de sus líneas (kilos en los productos por peso) */
  quantity: number;
  /** Total al costo, sin movimiento de dinero */
  total: number;
}

export interface ConsumptionsMonth {
  month: string;
  days: ConsumptionsDayRow[];
  totals: ConsumptionsDayRow;
}

export interface ConsumptionsSupplierRow {
  /** Mes de la tienda (YYYY-MM); null en el total del proveedor y del rango */
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
