import { Component, computed, input, output, signal } from '@angular/core';
import { Sale } from '../../../../core/cash/models/cash.models';
import { FinancialTransaction } from '../../../../core/finance/models/finance.models';
import { SalesTableComponent } from '../../../../shared/components/sales-table/sales-table.component';
import { CashTransfersComponent } from '../cash-transfers/cash-transfers.component';
import { CashExpensesComponent } from '../cash-expenses/cash-expenses.component';

export type SessionTransactionsTab = 'VENTAS' | 'COMPRAS' | 'MOVIMIENTOS';

// Transactions of the open session: sales, purchases (Caja expenses) and cash ins/outs (CAJ-08).
// The lists come from the POS; this card only picks the tab and forwards the actions.
@Component({
  selector: 'app-session-transactions',
  standalone: true,
  imports: [SalesTableComponent, CashTransfersComponent, CashExpensesComponent],
  templateUrl: './session-transactions.component.html',
  styleUrls: ['./session-transactions.component.css']
})
export class SessionTransactionsComponent {
  readonly sales = input<Sale[]>([]);
  readonly expenses = input<FinancialTransaction[]>([]);
  readonly transfers = input<FinancialTransaction[]>([]);
  // Only an open session can void or register movements
  readonly canOperate = input<boolean>(false);

  readonly registerSale = output<void>();
  readonly registerExpense = output<void>();
  readonly viewDetail = output<Sale>();
  readonly voidSale = output<Sale>();
  readonly transfersChanged = output<string>();
  readonly expensesChanged = output<string>();

  activeTab = signal<SessionTransactionsTab>('VENTAS');

  // Voided expenses do not count as active
  activeExpensesCount = computed(() => this.expenses().filter((e) => e.status === 'ACTIVO').length);

  setActiveTab(tab: SessionTransactionsTab): void {
    this.activeTab.set(tab);
  }
}
