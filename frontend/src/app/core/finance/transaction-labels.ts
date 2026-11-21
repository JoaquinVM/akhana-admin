import { Pipe, PipeTransform } from '@angular/core';
import { FinancialTransaction, FinancialTransactionStatus } from './models/finance.models';

type LabeledTransaction = Pick<FinancialTransaction, 'type'>
  & Partial<Pick<FinancialTransaction, 'purchaseId' | 'creditorRepaymentId' | 'customerCollectionId' | 'cashSessionId' | 'saleId'>>;

// H6 (096): the type of a movement in Spanish, telling apart what the code calls the same
// (a cash expense and a purchase payment are both GASTO_MANUAL)
export function transactionTypeLabel(tx: LabeledTransaction): string {
  switch (tx.type) {
    case 'VENTA_INGRESO':
      return 'Venta';
    case 'SALDO_INICIAL':
      return 'Saldo inicial';
    case 'TRANSFERENCIA':
      return 'Transferencia';
    case 'INGRESO_MANUAL':
      return tx.customerCollectionId ? 'Cobro a cliente' : 'Ingreso';
    case 'GASTO_MANUAL':
      if (tx.purchaseId) return 'Pago de pedido';
      if (tx.creditorRepaymentId) return 'Devolución a persona';
      return tx.cashSessionId ? 'Gasto de caja' : 'Gasto';
    default:
      return tx.type;
  }
}

export function transactionStatusLabel(status: FinancialTransactionStatus): string {
  return status === 'ANULADO' ? 'Anulado' : 'Activo';
}

// The sign of a movement: + when money comes in, - when it goes out. Seen from an account, a transfer goes out of
// its source and comes into its destination; without an account it has no sign
export function transactionSign(
  tx: Pick<FinancialTransaction, 'type'> & { sourceAccount?: { id: string } | null },
  accountId?: string | null
): '+' | '-' | '' {
  switch (tx.type) {
    case 'VENTA_INGRESO':
    case 'INGRESO_MANUAL':
    case 'SALDO_INICIAL':
      return '+';
    case 'GASTO_MANUAL':
      return '-';
    case 'TRANSFERENCIA':
      if (!accountId) return '';
      return tx.sourceAccount?.id === accountId ? '-' : '+';
    default:
      return '';
  }
}

@Pipe({ name: 'txType', standalone: true })
export class TransactionTypePipe implements PipeTransform {
  transform(tx: LabeledTransaction): string {
    return transactionTypeLabel(tx);
  }
}

@Pipe({ name: 'txStatus', standalone: true })
export class TransactionStatusPipe implements PipeTransform {
  transform(status: FinancialTransactionStatus): string {
    return transactionStatusLabel(status);
  }
}

// H6: the two system «Ajuste» categories (surplus and shortage of a closing) read apart
export function categoryLabel(category: { name: string; type: 'INGRESO' | 'GASTO' }): string {
  if (category.name === 'Ajuste') {
    return category.type === 'INGRESO' ? 'Ajuste (ingreso)' : 'Ajuste (gasto)';
  }
  return category.name;
}

export function categoryTypeLabel(type: 'INGRESO' | 'GASTO'): string {
  return type === 'INGRESO' ? 'Ingreso' : 'Gasto';
}

@Pipe({ name: 'categoryLabel', standalone: true })
export class CategoryLabelPipe implements PipeTransform {
  transform(category: { name: string; type: 'INGRESO' | 'GASTO' }): string {
    return categoryLabel(category);
  }
}

@Pipe({ name: 'categoryType', standalone: true })
export class CategoryTypePipe implements PipeTransform {
  transform(type: 'INGRESO' | 'GASTO'): string {
    return categoryTypeLabel(type);
  }
}
