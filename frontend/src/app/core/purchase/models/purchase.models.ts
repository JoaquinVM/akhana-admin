import { SaleUnit } from '../../product/models/product.models';

// COM-04: derivado de los pagos frente al total de las líneas (el envío no cuenta)
export type PurchasePaymentStatus = 'POR_PAGAR' | 'CONSIGNACION' | 'A_CUENTA' | 'PAGADO';

export type PurchaseStatus = 'REGISTRADO' | 'ANULADO';

export const PAYMENT_STATUS_LABELS: Record<PurchasePaymentStatus, string> = {
  POR_PAGAR: 'Por pagar',
  CONSIGNACION: 'Consignación',
  A_CUENTA: 'A cuenta',
  PAGADO: 'Pagado'
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
  accountId: string;
  accountName: string;
  cashAccount: boolean;
  amount: number;
  paymentDate: string;
  financialTransactionId: string;
  status: 'ACTIVO' | 'ANULADO';
  createdBy: string;
  createdAt: string;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
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
  shippingAccountName: string | null;
  createdBy: string;
  createdAt: string;
  items: PurchaseItem[];
  payments: PurchasePayment[];
}

export interface PurchaseItemRequest {
  productId: string;
  quantity: number;
  unitCost: number;
  // COM-03: solo si el usuario confirmó la alerta de costo
  updateProductCost: boolean;
  newSellPrice: number | null;
}

export interface PurchaseRequest {
  supplierId: string;
  purchaseDate: string | null;
  items: PurchaseItemRequest[];
  shippingCost: number;
  shippingAccountId: string | null;
  consignment: boolean;
  comment: string | null;
}

export interface PurchasePaymentRequest {
  accountId: string;
  amount: number;
  notes: string | null;
}

export interface PaymentAccount {
  id: string;
  name: string;
  color: string;
  cashAccount: boolean;
}

export interface PriceSuggestion {
  productId: string;
  currentCost: number;
  currentSellPrice: number;
  currentPercentageProfit: number;
  newCost: number;
  // null si el costo actual es 0: no hay utilidad porcentual que mantener
  suggestedSellPrice: number | null;
}

export interface PurchaseFilters {
  supplierId?: string | null;
  paymentStatus?: PurchasePaymentStatus | null;
  search?: string | null;
}
