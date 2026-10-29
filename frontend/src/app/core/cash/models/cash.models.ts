export type CashSessionStatus = 'ABIERTA' | 'CERRADA';
// CREDITO: at-cost sale fully on credit, with no payment now (DEU-05)
// HISTORICO: sale migrated from the spreadsheet, without payments; its money is in the movements (FIN-11)
export type PaymentMethod = 'EFECTIVO' | 'QR' | 'MIXTO' | 'CREDITO' | 'HISTORICO';
// CON-02: the whole sale is normal or at cost (price = cost, no profit)
export type SaleType = 'NORMAL' | 'AL_COSTO';
export type SaleStatus = 'COMPLETADA' | 'ANULADA';

export interface CashCutItem {
  denomination: number;
  cashQuantity: number;
  reserveQuantity: number;
  subtotal: number;
}

// CAJ-10: opening amount proposed from the previous session's closing
export interface CashOpeningProposal {
  proposedAmount: number;
  previousSessionNumber: number | null;
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
  // How the product is sold: a line by kilo without presentation shows kilos and price per kilo
  saleUnit?: 'UNIT' | 'KILOGRAM' | null;
  // CAT-11: copy of the sold presentation and the line's kilos
  presentationId?: string | null;
  presentationName?: string | null;
  presentationWeightKg?: number | null;
  weightKg?: number | null;
  // POS-11 to POS-13: free line with its typed name, its assignment and the owner
  freeLineType?: 'UNIDENTIFIED' | 'THIRD_PARTY' | null;
  freeName?: string | null;
  assignmentStatus?: SaleItemAssignmentStatus | null;
  assignedBy?: string | null;
  assignedAt?: string | null;
  creditorId?: string | null;
  creditorName?: string | null;
}

export type SaleItemAssignmentStatus = 'POR_ASIGNAR' | 'ASIGNADO' | 'SIN_IDENTIFICAR';

export interface Sale {
  id: string;
  saleNumber: string;
  // Null for historical sales (FIN-11)
  cashSessionId: string | null;
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
  // Part of the sale left to collect from the customer (DEU-05)
  creditAmount?: number;
  // FIN-11: migrated from the spreadsheet; never voided or edited
  historical?: boolean;
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
  // FIN-11: session from the Cierre de Caja sheet
  historical?: boolean;
}

export interface CashSessionDetail extends CashSessionSummary {
  cuts: CashCutItem[];
  sales: Sale[];
  // FIN-11: spreadsheet amounts the system otherwise computes from movements (null for other sessions)
  historicalOtherIncome?: number | null;
  historicalExpenses?: number | null;
  historicalOtherOutflow?: number | null;
  historicalInternalOutflow?: number | null;
}

export const BOLIVIAN_DENOMINATIONS: readonly number[] = [
  200.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 1.0, 0.5, 0.2, 0.1
];
