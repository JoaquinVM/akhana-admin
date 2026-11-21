import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { TransactionStatusPipe, TransactionTypePipe, transactionSign } from '../../../core/finance/transaction-labels';
import { FinancialTransaction } from '../../../core/finance/models/finance.models';
import { FinanceSummaryService } from '../../../core/finance/finance-summary.service';
import { FinancialSummary } from '../../../core/finance/models/finance.models';
import { operationDayLabel } from '../../../shared/utils/store-date';

import { PluralPipe } from '../../../shared/utils/plural.pipe';

@Component({
  selector: 'app-finance-summary',
  standalone: true,
  imports: [PluralPipe, CommonModule, RouterLink, TransactionTypePipe, TransactionStatusPipe],
  templateUrl: './finance-summary.component.html',
  styleUrls: ['./finance-summary.component.css']
})
export class FinanceSummaryComponent implements OnInit {
  // H6: + or -, a transfer has no sign in a list of all accounts
  txSign(tx: FinancialTransaction): string {
    return transactionSign(tx);
  }

  // CAJ-11: «Día 31/10 · cargada el 01/11 00:30», as in the movements list
  readonly dayMark = operationDayLabel;

  private readonly summaryService = inject(FinanceSummaryService);

  summary = signal<FinancialSummary | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadSummary();
  }

  loadSummary(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.summaryService.getSummary().subscribe({
      next: (data) => {
        this.summary.set(data);
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err.error?.message || 'Error al cargar el resumen financiero.');
        this.isLoading.set(false);
      }
    });
  }
}
