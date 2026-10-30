// DSH-01 to DSH-04: the dashboard as returned by GET /api/dashboard (specs/dashboard.md)

export interface DashboardIndicators {
  sales: number;
  cost: number;
  profit: number;
  margin: number | null;
  tickets: number;
  averageTicket: number | null;
  atCostSales: number;
  atCostTotal: number;
  consumptionTotal: number;
}

export interface DashboardDayPoint {
  date: string;
  sales: number;
  profit: number;
}

export interface DashboardPayments {
  cash: number;
  qr: number;
  credit: number;
}

export interface DashboardRankRow {
  id: string | null;
  name: string;
  quantity: number;
  sales: number;
  profit: number;
}

export interface DashboardCategoryAmount {
  categoryId: string | null;
  categoryName: string;
  amount: number;
}

export interface DashboardCashNow {
  open: boolean;
  sessionNumber: number | null;
  openedBy: string | null;
  openedAt: string | null;
  openingAmount: number | null;
  totalSales: number | null;
  salesCount: number;
  expectedCash: number | null;
}

export interface DashboardAccountBalance {
  id: string;
  name: string;
  color: string | null;
  balance: number;
}

export interface DashboardNow {
  cash: DashboardCashNow;
  accounts: DashboardAccountBalance[];
  totalBalance: number;
  payables: number;
  creditorsDebt: number;
  receivables: number;
}

export interface Dashboard {
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  current: DashboardIndicators;
  previous: DashboardIndicators;
  days: DashboardDayPoint[];
  previousDays: DashboardDayPoint[];
  payments: DashboardPayments;
  topProductsBySales: DashboardRankRow[];
  topProductsByProfit: DashboardRankRow[];
  topSuppliersBySales: DashboardRankRow[];
  topSuppliersByProfit: DashboardRankRow[];
  expenses: DashboardCategoryAmount[];
  expenseTotal: number;
  now: DashboardNow;
}

export interface DashboardRange {
  from?: string | null;
  to?: string | null;
}
