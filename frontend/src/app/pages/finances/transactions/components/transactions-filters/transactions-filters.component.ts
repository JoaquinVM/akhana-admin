import { Component, input, model, output } from '@angular/core';
import {
  FinancialAccount,
  FinancialCategory,
  FinancialTransactionStatus,
  FinancialTransactionType
} from '../../../../../core/finance/models/finance.models';

// Filtros del listado de transacciones; el padre guarda los valores y vuelve a consultar
@Component({
  selector: 'app-transactions-filters',
  standalone: true,
  templateUrl: './transactions-filters.component.html',
  styleUrls: ['./transactions-filters.component.css']
})
export class TransactionsFiltersComponent {
  accounts = input<FinancialAccount[]>([]);
  categories = input<FinancialCategory[]>([]);

  startDate = model<string>('');
  endDate = model<string>('');
  selectedType = model<FinancialTransactionType | ''>('');
  selectedAccountId = model<string>('');
  selectedCategoryId = model<string>('');
  selectedStatus = model<FinancialTransactionStatus | ''>('');
  searchTerm = model<string>('');

  filterChange = output<void>();
  filtersReset = output<void>();
}
