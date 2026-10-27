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

// COM-07: mientras está pendiente la fecha es la estimada; entregado, la de la entrega
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
  // Un solo origen: una cuenta o una persona que pagó por el negocio (DEU-02)
  accountId: string | null;
  accountName: string | null;
  cashAccount: boolean;
  creditorId: string | null;
  creditorName: string | null;
  amount: number;
  paymentDate: string;
  financialTransactionId: string | null;
  status: 'ACTIVO' | 'ANULADO';
  createdBy: string;
  createdAt: string;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  // FIN-11: pago migrado de la planilla, sin movimiento de dinero (P-43)
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
  shippingAccountName: string | null;
  deliveryType: DeliveryType;
  deliveryStatus: DeliveryStatus;
  deliveryDate: string | null;
  createdBy: string;
  createdAt: string;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  items: PurchaseItem[];
  payments: PurchasePayment[];
  // FIN-11: migrado de la planilla; solo admite pagos mientras tenga saldo
  historical?: boolean;
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
  deliveryType: DeliveryType;
  deliveryStatus: DeliveryStatus;
  deliveryDate: string | null;
  // Pago inicial opcional, desde una cuenta o hecho por una persona (COM-05, DEU-02)
  initialPayment: PurchasePaymentRequest | null;
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

// COM-08: pedidos con saldo agrupados por proveedor
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

// El origen del pago se elige en un solo select: 'account:<id>' o 'creditor:<id>'
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
  // Cuenta de cobros QR (FIN-03): junto con Caja, la única que recibe cobros a clientes (DEU-06)
  qrAccount: boolean;
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
  status?: PurchaseStatus | null;
  paymentStatus?: PurchasePaymentStatus | null;
  deliveryStatus?: DeliveryStatus | null;
  search?: string | null;
}
