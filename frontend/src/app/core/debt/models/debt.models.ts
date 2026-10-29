import { PaymentAccount } from '../../purchase/models/purchase.models';

export type CreditorStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// DEU-01 / DEU-04: person the business owes money to; the server computes the debt
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

// DEU-04: purchase payments made by the person (PAYMENT) and repayments (REPAYMENT)
// POS-13: SALE is a sale with the person's products (adds to the debt)
export type CreditorHistoryEntryType = 'PAYMENT' | 'SALE' | 'REPAYMENT';

// What is repaid: purchases she paid (category Pedido) or sales of her products (Pago a tercero)
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
  // Debt after the entry; a voided one does not change it
  balanceAfter: number;
}

export interface CreditorHistory {
  creditor: Creditor;
  entries: CreditorHistoryEntry[];
}

// DEU-03: repayment from Caja (with an open session) or another account
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

// DEU-05 / DEU-07: customer buying at cost on credit; the server computes the receivable
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

// DEU-07: sales partly on credit (SALE) and collections (COLLECTION)
export type CustomerHistoryEntryType = 'SALE' | 'COLLECTION';

export interface CustomerHistoryEntry {
  type: CustomerHistoryEntryType;
  id: string;
  date: string;
  // SALE: the credit part; COLLECTION: what was collected
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
  // Receivable after the entry; a voided one does not change it
  balanceAfter: number;
}

export interface CustomerHistory {
  customer: Customer;
  entries: CustomerHistoryEntry[];
}

// DEU-06: collection into Caja (with an open session) or the QR account
export interface CustomerCollectionRequest {
  accountId: string;
  amount: number;
  comment: string | null;
}
