export type CashSessionStatus = 'ABIERTA' | 'CERRADA';
export type PaymentMethod = 'EFECTIVO' | 'QR';

export interface CashCutItem {
  denomination: number;
  cashQuantity: number;
  reserveQuantity: number;
  subtotal: number;
}

export interface OpenCashRequest {
  openingAmount: number;
  openingComment?: string | null;
}

export interface CloseCashRequest {
  closingAmount: number;
  closingComment?: string | null;
  cuts?: CashCutItem[];
}

export interface SaleRequest {
  totalAmount: number;
  paymentMethod: PaymentMethod;
  description?: string | null;
}

export interface Sale {
  id: string;
  saleNumber: string;
  cashSessionId: string;
  totalAmount: number;
  paymentMethod: PaymentMethod;
  description?: string | null;
  createdBy: string;
  createdAt: string;
}

export interface CashSessionSummary {
  id: string;
  sessionNumber: number;
  status: CashSessionStatus;
  openingAmount: number;
  openingComment?: string | null;
  openedBy: string;
  openedAt: string;
  closingAmount?: number | null;
  closingComment?: string | null;
  closedBy?: string | null;
  closedAt?: string | null;
  totalSalesCash: number;
  totalSalesQr: number;
  totalSales: number;
  expectedCash: number;
  difference?: number | null;
  salesCount: number;
}

export interface CashSessionDetail extends CashSessionSummary {
  cuts: CashCutItem[];
  sales: Sale[];
}

export const BOLIVIAN_DENOMINATIONS: readonly number[] = [
  200.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 1.0, 0.5, 0.2, 0.1
];
