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
export type CreditorHistoryEntryType = 'PAYMENT' | 'REPAYMENT';

export interface CreditorHistoryEntry {
  type: CreditorHistoryEntryType;
  id: string;
  date: string;
  amount: number;
  purchaseId: string | null;
  purchaseNumber: string | null;
  supplierName: string | null;
  accountId: string | null;
  accountName: string | null;
  comment: string | null;
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
}

export interface VoidDebtRequest {
  voidReason: string;
}

export type DebtAccount = PaymentAccount;
