import { Component, input, model, output } from '@angular/core';
import {
  FinancialAccount,
  FinancialCategory,
  FinancialTransactionStatus,
  FinancialTransactionType
} from '../../../../../core/finance/models/finance.models';

// Transaction list filters; the parent keeps the values and queries again
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
