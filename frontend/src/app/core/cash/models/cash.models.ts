export type CashSessionStatus = 'ABIERTA' | 'CERRADA';
// CREDITO: venta al costo toda a crédito, sin pago en el momento (DEU-05)
// HISTORICO: venta migrada de la planilla, sin pagos; su dinero está en los movimientos (FIN-11)
export type PaymentMethod = 'EFECTIVO' | 'QR' | 'MIXTO' | 'CREDITO' | 'HISTORICO';
// CON-02: toda la venta es normal o al costo (precio = costo, sin utilidad)
export type SaleType = 'NORMAL' | 'AL_COSTO';
export type SaleStatus = 'COMPLETADA' | 'ANULADA';

export interface CashCutItem {
  denomination: number;
  cashQuantity: number;
  reserveQuantity: number;
  subtotal: number;
}

// CAJ-10: monto inicial propuesto a partir del cierre de la caja anterior
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
  // Cómo se vende el producto: una línea por kilo sin presentación muestra kilos y precio por kilo
  saleUnit?: 'UNIT' | 'KILOGRAM' | null;
  // CAT-11: copia de la presentación vendida y los kilos de la línea
  presentationId?: string | null;
  presentationName?: string | null;
  presentationWeightKg?: number | null;
  weightKg?: number | null;
  // POS-11 a POS-13: línea libre con el nombre escrito, su asignación y la dueña
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
  // Null en las ventas históricas (FIN-11)
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
  // Parte de la venta que queda por cobrar al cliente (DEU-05)
  creditAmount?: number;
  // FIN-11: migrado de la planilla; no se anula ni se edita
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
  // FIN-11: caja de la hoja Cierre de Caja
  historical?: boolean;
}

export interface CashSessionDetail extends CashSessionSummary {
  cuts: CashCutItem[];
  sales: Sale[];
  // FIN-11: montos de la planilla que el sistema calcula de los movimientos (null en las demás cajas)
  historicalOtherIncome?: number | null;
  historicalExpenses?: number | null;
  historicalOtherOutflow?: number | null;
  historicalInternalOutflow?: number | null;
}

export const BOLIVIAN_DENOMINATIONS: readonly number[] = [
  200.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 1.0, 0.5, 0.2, 0.1
];
