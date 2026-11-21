import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FinancialTransaction } from '../../../../../core/finance/models/finance.models';
import { operationDayLabel } from '../../../../../shared/utils/store-date';
import { CategoryLabelPipe, TransactionStatusPipe, TransactionTypePipe, transactionSign } from '../../../../../core/finance/transaction-labels';

// Money movements list with its row actions
@Component({
  selector: 'app-transactions-table',
  standalone: true,
  imports: [CommonModule, TransactionTypePipe, TransactionStatusPipe, CategoryLabelPipe],
  templateUrl: './transactions-table.component.html',
  styleUrls: ['./transactions-table.component.css']
})
export class TransactionsTableComponent {
  // CAJ-11: «Día 31/10 · cargada el 01/11 00:30» when it counts for another day than the one it was recorded
  dayMark(tx: FinancialTransaction): string | null {
    return operationDayLabel(tx.operationDate, tx.transactionDate);
  }

  transactions = input<FinancialTransaction[]>([]);

  // H6: + or -, a transfer has no sign in a list of all accounts
  txSign(tx: FinancialTransaction): string {
    return transactionSign(tx);
  }

  saleDetailRequested = output<string>();
  editRequested = output<FinancialTransaction>();
  voidRequested = output<FinancialTransaction>();
  auditRequested = output<FinancialTransaction>();

  // Historical movements (FIN-11) are never edited or voided; the server rejects it too
  canModify(tx: FinancialTransaction): boolean {
    return tx.status === 'ACTIVO' && !tx.historical;
  }
}
