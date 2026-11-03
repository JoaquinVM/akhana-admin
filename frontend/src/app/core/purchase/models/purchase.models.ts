import { SaleUnit } from '../../product/models/product.models';

// COM-04: derived from the payments against the lines total (shipping does not count)
export type PurchasePaymentStatus = 'POR_PAGAR' | 'CONSIGNACION' | 'A_CUENTA' | 'PAGADO';

export type PurchaseStatus = 'REGISTRADO' | 'ANULADO';

export const PAYMENT_STATUS_LABELS: Record<PurchasePaymentStatus, string> = {
  POR_PAGAR: 'Por pagar',
  CONSIGNACION: 'Consignación',
  A_CUENTA: 'A cuenta',
  PAGADO: 'Pagado'
};

// COM-07: while pending the date is the estimate; once delivered, the delivery date
export type DeliveryType = 'EN_PUERTA' | 'TERMINAL' | 'EN_TIENDA';

export type DeliveryStatus = 'PENDIENTE' | 'ENTREGADO';

export const DELIVERY_TYPE_LABELS: Record<DeliveryType, string> = {
  EN_PUERTA: 'En puerta',
  TERMINAL: 'Terminal',
  EN_TIENDA: 'En tienda'
};

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDIENTE: 'Pendiente',
  ENTREGADO: 'Entregado'
};

export interface PurchaseItem {
  id: string;
  productId: string;
  productName: string;
  productCode: string | null;
  saleUnit: SaleUnit;
  quantity: number;
  unitCost: number;
  subtotal: number;
  previousUnitCost: number;
  costUpdated: boolean;
}

export interface PurchasePayment {
  id: string;
  // One source: an account or a person who paid for the business (DEU-02)
  accountId: string | null;
  accountName: string | null;
  cashAccount: boolean;
  creditorId: string | null;
  creditorName: string | null;
  amount: number;
  paymentDate: string;
  // CAJ-11: day it counts for (yyyy-MM-dd); paymentDate is the real time it was recorded
  operationDate?: string | null;
  financialTransactionId: string | null;
  status: 'ACTIVO' | 'ANULADO';
  createdBy: string;
  createdAt: string;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  // FIN-11: payment migrated from the spreadsheet, without a money movement (P-43)
  historical?: boolean;
}

export interface Purchase {
  id: string;
  purchaseNumber: string;
  supplierId: string;
  supplierName: string;
  purchaseDate: string;
  status: PurchaseStatus;
  itemsTotal: number;
  shippingCost: number;
  totalAmount: number;
  paidAmount: number;
  balance: number;
  paymentStatus: PurchasePaymentStatus;
  consignment: boolean;
  comment: string | null;
  shippingAccountId: string | null;
  shippingAccountName: string | null;
  deliveryType: DeliveryType;
  deliveryStatus: DeliveryStatus;
  deliveryDate: string | null;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  items: PurchaseItem[];
  payments: PurchasePayment[];
  // COM-12: edits, the most recent first
  revisions: PurchaseRevision[];
  // FIN-11: migrated from the spreadsheet; only takes payments while it has a balance
  historical?: boolean;
  // COM-22: its costs are the system's until the real ones are registered; the amount is an estimate
  costToConfirm?: boolean;
  // COM-23: the active invoice photo; only in the detail
  photoId?: string | null;
}

// COM-12: who edited a purchase, when, why and what changed
export interface PurchaseRevision {
  id: string;
  reason: string;
  changes: string;
  revisedBy: string;
  revisedAt: string;
}

export interface PurchaseItemRequest {
  productId: string;
  quantity: number;
  unitCost: number;
  // COM-03: only if the user confirmed the cost alert
  updateProductCost: boolean;
  newSellPrice: number | null;
  // COM-11: the line it keeps when editing; absent for a new line
  itemId?: string | null;
}

export interface PurchaseRequest {
  supplierId: string;
  purchaseDate: string | null;
  items: PurchaseItemRequest[];
  shippingCost: number;
  shippingAccountId: string | null;
  consignment: boolean;
  comment: string | null;
  deliveryType: DeliveryType;
  deliveryStatus: DeliveryStatus;
  deliveryDate: string | null;
  // Optional first payment, from an account or made by a person (COM-05, DEU-02)
  initialPayment: PurchasePaymentRequest | null;
  // COM-20: the reading this purchase comes from
  readingId?: string;
}

// COM-11: the whole purchase as it should be, without payments, with a mandatory reason
export interface PurchaseUpdateRequest extends Omit<PurchaseRequest, 'initialPayment'> {
  reason: string;
  // COM-22: the goods arrived: it ends delivered and without «costo por confirmar»
  arrival?: boolean;
}

// COM-20: what a reading proposes for the form; a person corrects and registers it
export type PurchaseReadingSource = 'TEXT' | 'PHOTO';

export interface PurchaseDraftSuggestion {
  id: string;
  code: string;
  name: string;
  supplierName: string | null;
}

export interface PurchaseDraftLine {
  text: string;
  quantity: number;
  quantityDoubtful: boolean;
  quantityText: string | null;
  // Null: the person chooses it among the suggestions or with the search box (CA-03)
  productId: string | null;
  suggestions: PurchaseDraftSuggestion[];
  // Null: it has to be filled before registering (CA-15)
  unitCost: number | null;
  costNote: string | null;
}

export interface PendingPurchase {
  id: string;
  purchaseNumber: string;
  purchaseDate: string;
  itemsTotal: number;
  costToConfirm: boolean;
}

export interface PurchaseDraft {
  readingId: string;
  source: PurchaseReadingSource;
  supplierId: string | null;
  supplierText: string | null;
  reviewAll: boolean;
  paidNote: boolean;
  lines: PurchaseDraftLine[];
  shippingCost: number | null;
  readTotal: number | null;
  pendingPurchases: PendingPurchase[];
}

// COM-25: the spend comes only for an administrator
export interface PurchaseReadingStatus {
  configured: boolean;
  capReached: boolean;
  spentUsd: number | null;
  capUsd: number | null;
}

export interface PurchaseDeliveryRequest {
  deliveryType: DeliveryType;
  deliveryStatus: DeliveryStatus;
  deliveryDate: string | null;
}

// COM-09: anular pedido o pago siempre exige motivo
export interface VoidPurchaseRequest {
  voidReason: string;
}

// COM-08: purchases with a balance grouped by supplier
export interface SupplierPayables {
  supplierId: string;
  supplierName: string;
  balance: number;
  purchases: Purchase[];
}

export interface Payables {
  totalBalance: number;
  suppliers: SupplierPayables[];
}

export interface PurchasePaymentRequest {
  accountId: string | null;
  creditorId: string | null;
  amount: number;
  notes: string | null;
}

// The payment source is picked in one select: 'account:<id>' or 'creditor:<id>'
export function paymentSourceFields(source: string): Pick<PurchasePaymentRequest, 'accountId' | 'creditorId'> {
  const [kind, id] = source.split(':');
  return {
    accountId: kind === 'account' && id ? id : null,
    creditorId: kind === 'creditor' && id ? id : null
  };
}

export function paymentSourceName(payment: PurchasePayment): string {
  return payment.creditorName ? `${payment.creditorName} (persona)` : payment.accountName ?? '';
}

export interface PaymentAccount {
  id: string;
  name: string;
  color: string;
  cashAccount: boolean;
  // QR collection account (FIN-03): with Caja, the only one that receives customer collections (DEU-06)
  qrAccount: boolean;
}

export interface PriceSuggestion {
  productId: string;
  currentCost: number;
  currentSellPrice: number;
  currentPercentageProfit: number;
  newCost: number;
  // null when the current cost is 0: there is no percentage profit to keep
  suggestedSellPrice: number | null;
}

export interface PurchaseFilters {
  supplierId?: string | null;
  status?: PurchaseStatus | null;
  paymentStatus?: PurchasePaymentStatus | null;
  deliveryStatus?: DeliveryStatus | null;
  search?: string | null;
}
