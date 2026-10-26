export type FinancialCategoryType = 'INGRESO' | 'GASTO';

export type FinancialTransactionType =
  | 'INGRESO_MANUAL'
  | 'GASTO_MANUAL'
  | 'TRANSFERENCIA'
  | 'SALDO_INICIAL'
  | 'VENTA_INGRESO';

export type FinancialTransactionStatus = 'ACTIVO' | 'ANULADO';

export interface FinancialAccount {
  id: string;
  name: string;
  color: string;
  initialBalance: number;
  currentBalance: number;
  isCashAccount: boolean;
  isQrAccount: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
  deletedBy?: string;
  deletedAt?: string;
}

export interface FinancialAccountRequest {
  name: string;
  color?: string;
  initialBalance?: number;
  isQrAccount?: boolean;
}

export interface FinancialCategory {
  id: string;
  name: string;
  type: FinancialCategoryType;
  color: string;
  isSalesDefault: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
  deletedBy?: string;
  deletedAt?: string;
}

export interface FinancialCategoryRequest {
  name: string;
  type: FinancialCategoryType;
  color?: string;
  isSalesDefault?: boolean;
}

export interface AccountSummary {
  id: string;
  name: string;
  color: string;
  cashAccount?: boolean;
}

export interface CategorySummary {
  id: string;
  name: string;
  type: FinancialCategoryType;
  color?: string;
}

export interface FinancialTransaction {
  id: string;
  type: FinancialTransactionType;
  amount: number;
  transactionDate: string;
  description: string;
  notes?: string;
  status: FinancialTransactionStatus;
  saleId?: string;
  // COM-06: pago o envío de un pedido; se gestiona desde Compras
  purchaseId?: string | null;
  // Gasto de una devolución a una persona (DEU-03): se gestiona desde Deudas
  creditorRepaymentId?: string | null;
  cashSessionId?: string;
  sourceAccount: AccountSummary;
  destinationAccount?: AccountSummary;
  category?: CategorySummary;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
  voidedBy?: string;
  voidedAt?: string;
  voidReason?: string;
}

export interface FinancialTransactionRequest {
  type: FinancialTransactionType;
  sourceAccountId: string;
  destinationAccountId?: string;
  categoryId?: string;
  cashSessionId?: string;
  amount: number;
  description: string;
  notes?: string;
}

export interface CashExpenseRequest {
  amount: number;
  categoryId: string;
  cashSessionId?: string;
  description: string;
  notes?: string;
}

// CAJ-08: ingreso o retiro de caja, visto desde la cuenta Caja
export type CashTransferDirection = 'INGRESO' | 'RETIRO';

export interface CashTransferRequest {
  direction: CashTransferDirection;
  accountId: string;
  amount: number;
  comment: string;
}

export interface CashTransferAccount {
  id: string;
  name: string;
  color?: string;
  cashAccount: boolean;
  qrAccount: boolean;
}

export interface VoidFinancialTransactionRequest {
  voidReason: string;
}

export interface FinancialSummary {
  cashBalance: number;
  bisaBalance: number;
  joacoBalance: number;
  totalBalance: number;
  qrAccountId?: string;
  qrAccountName?: string;
  accounts: FinancialAccount[];
  recentTransactions: FinancialTransaction[];
}

export interface TransactionFilterParams {
  startDate?: string;
  endDate?: string;
  type?: FinancialTransactionType;
  accountId?: string;
  categoryId?: string;
  status?: FinancialTransactionStatus;
  search?: string;
}
