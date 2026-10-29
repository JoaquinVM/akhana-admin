import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FinancialTransaction } from '../../../../../core/finance/models/finance.models';

// Money movements list with its row actions
@Component({
  selector: 'app-transactions-table',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './transactions-table.component.html',
  styleUrls: ['./transactions-table.component.css']
})
export class TransactionsTableComponent {
  transactions = input<FinancialTransaction[]>([]);

  saleDetailRequested = output<string>();
  editRequested = output<FinancialTransaction>();
  voidRequested = output<FinancialTransaction>();
  auditRequested = output<FinancialTransaction>();

  // Historical movements (FIN-11) are never edited or voided; the server rejects it too
  canModify(tx: FinancialTransaction): boolean {
    return tx.status === 'ACTIVO' && !tx.historical;
  }
}
