import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FinanceSummaryService } from '../../../core/finance/finance-summary.service';
import { FinancialSummary } from '../../../core/finance/models/finance.models';

@Component({
  selector: 'app-finance-summary',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './finance-summary.component.html',
  styleUrls: ['./finance-summary.component.css']
})
export class FinanceSummaryComponent implements OnInit {
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
      error: (err: any) => {
        this.errorMessage.set(err.error?.message || 'Error al cargar el resumen financiero.');
        this.isLoading.set(false);
      }
    });
  }
}
