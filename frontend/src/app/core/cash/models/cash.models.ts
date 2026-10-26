export type CashSessionStatus = 'ABIERTA' | 'CERRADA';
// CREDITO: venta al costo toda a crédito, sin pago en el momento (DEU-05)
export type PaymentMethod = 'EFECTIVO' | 'QR' | 'MIXTO' | 'CREDITO';
// CON-02: toda la venta es normal o al costo (precio = costo, sin utilidad)
export type SaleType = 'NORMAL' | 'AL_COSTO';
export type SaleStatus = 'COMPLETADA' | 'ANULADA';

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

export interface SaleItem {
  id?: string;
  productId: string;
  productName: string;
  productCode?: string;
  unitPrice: number;
  discountPerUnit: number;
  finalUnitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface Sale {
  id: string;
  saleNumber: string;
  cashSessionId: string;
  sessionNumber?: number | null;
  status?: SaleStatus;
  subtotalAmount?: number;
  discountItemsTotal?: number;
  globalDiscountAmount?: number;
  discountTotal?: number;
  totalAmount: number;
  paymentMethod: PaymentMethod;
  amountCash?: number;
  amountQr?: number;
  amountReceived?: number;
  changeGiven?: number;
  description?: string | null;
  createdBy: string;
  createdAt: string;
  voidedAt?: string | null;
  voidedBy?: string | null;
  voidReason?: string | null;
  items?: SaleItem[];
  saleType?: SaleType;
  customerId?: string | null;
  customerName?: string | null;
  // Parte de la venta que queda por cobrar al cliente (DEU-05)
  creditAmount?: number;
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
