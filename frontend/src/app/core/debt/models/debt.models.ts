import { PaymentAccount } from '../../purchase/models/purchase.models';

export type CreditorStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// DEU-01 / DEU-04: persona a la que el negocio le debe; la deuda la calcula el servidor
export interface Creditor {
  id: string;
  name: string;
  phone: string | null;
  description: string | null;
  status: CreditorStatus;
  debt: number;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  deletedBy: string | null;
  deletedAt: string | null;
}

export interface CreditorRequest {
  name: string;
  phone: string | null;
  description: string | null;
  status?: CreditorStatus;
}

// DEU-04: pagos de pedidos hechos por la persona (PAYMENT) y devoluciones (REPAYMENT)
// POS-13: SALE es una venta con productos de la persona (suma a la deuda)
export type CreditorHistoryEntryType = 'PAYMENT' | 'SALE' | 'REPAYMENT';

// Qué devuelve: pedidos que pagó (categoría Pedido) o ventas de sus productos (Pago a tercero)
export type CreditorRepaymentConcept = 'PURCHASE' | 'THIRD_PARTY';

export interface CreditorHistoryEntry {
  type: CreditorHistoryEntryType;
  id: string;
  date: string;
  amount: number;
  purchaseId: string | null;
  purchaseNumber: string | null;
  supplierName: string | null;
  saleId: string | null;
  saleNumber: string | null;
  accountId: string | null;
  accountName: string | null;
  comment: string | null;
  concept: CreditorRepaymentConcept | null;
  voided: boolean;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  // Deuda después del registro; un anulado no la cambia
  balanceAfter: number;
}

export interface CreditorHistory {
  creditor: Creditor;
  entries: CreditorHistoryEntry[];
}

// DEU-03: devolución desde Caja (con caja abierta) u otra cuenta
export interface CreditorRepaymentRequest {
  accountId: string;
  amount: number;
  comment: string | null;
  concept?: CreditorRepaymentConcept;
}

export interface VoidDebtRequest {
  voidReason: string;
}

export type DebtAccount = PaymentAccount;

export type CustomerStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// DEU-05 / DEU-07: cliente que compra al costo a crédito; el saldo por cobrar lo calcula el servidor
export interface Customer {
  id: string;
  name: string;
  phone: string | null;
  description: string | null;
  status: CustomerStatus;
  balance: number;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  deletedBy: string | null;
  deletedAt: string | null;
}

export interface CustomerRequest {
  name: string;
  phone: string | null;
  description: string | null;
  status?: CustomerStatus;
}

// DEU-07: ventas con parte a crédito (SALE) y cobros (COLLECTION)
export type CustomerHistoryEntryType = 'SALE' | 'COLLECTION';

export interface CustomerHistoryEntry {
  type: CustomerHistoryEntryType;
  id: string;
  date: string;
  // SALE: la parte a crédito; COLLECTION: lo cobrado
  amount: number;
  saleId: string | null;
  saleNumber: string | null;
  saleTotal: number | null;
  accountId: string | null;
  accountName: string | null;
  comment: string | null;
  voided: boolean;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  // Saldo por cobrar después del registro; un anulado no lo cambia
  balanceAfter: number;
}

export interface CustomerHistory {
  customer: Customer;
  entries: CustomerHistoryEntry[];
}

// DEU-06: cobro a Caja (con caja abierta) o a la cuenta QR
export interface CustomerCollectionRequest {
  accountId: string;
  amount: number;
  comment: string | null;
}
