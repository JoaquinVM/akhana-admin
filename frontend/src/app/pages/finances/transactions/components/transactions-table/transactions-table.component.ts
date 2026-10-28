import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FinancialTransaction } from '../../../../../core/finance/models/finance.models';

// Listado de transacciones financieras con sus acciones por fila
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

  // Los movimientos históricos (FIN-11) no se editan ni se anulan; el servidor también los rechaza
  canModify(tx: FinancialTransaction): boolean {
    return tx.status === 'ACTIVO' && !tx.historical;
  }
}
