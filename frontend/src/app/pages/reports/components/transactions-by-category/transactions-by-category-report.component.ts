import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportService } from '../../../../core/report/report.service';
import {
  CategoryAmount, TransactionsByCategoryMonth, TransactionsByCategoryReport, TransferAmount
} from '../../../../core/report/models/report.models';
import { monthLabel } from '../../../../core/report/report-labels';

interface ReportLine {
  key: string;
  label: string;
  /** Monto por columna: un mes o el total del rango; null si no hubo movimientos */
  amounts: (number | null)[];
}

const categoryKey = (c: CategoryAmount) => c.categoryId ?? 'none';
const transferKey = (t: TransferAmount) => `${t.sourceAccountId}>${t.destinationAccountId}`;

// FIN-10 / CON-03: movimientos por categoría y mes, transferencias y consumo aparte; reemplaza Resumen Transacc.
@Component({
  selector: 'app-transactions-by-category-report',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './transactions-by-category-report.component.html',
  styleUrls: ['../sales-by-day/sales-by-day-report.component.css', './transactions-by-category-report.component.css']
})
export class TransactionsByCategoryReportComponent implements OnInit {
  private readonly reportService = inject(ReportService);

  report = signal<TransactionsByCategoryReport | null>(null);
  fromDate = signal<string>('');
  toDate = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // Una columna por mes y la última con el total del rango, como la tabla dinámica
  columns = computed<TransactionsByCategoryMonth[]>(() => {
    const r = this.report();
    return r ? [...r.months, r.totals] : [];
  });

  // Las filas salen del total del rango, que ya trae todas las categorías y transferencias ordenadas
  incomeLines = computed(() =>
    this.lines(this.report()?.totals.incomes ?? [], m => m.incomes, categoryKey, c => c.categoryName));
  expenseLines = computed(() =>
    this.lines(this.report()?.totals.expenses ?? [], m => m.expenses, categoryKey, c => c.categoryName));
  transferLines = computed(() =>
    this.lines(this.report()?.totals.transfers ?? [], m => m.transfers, transferKey,
      t => `${t.sourceAccountName} → ${t.destinationAccountName}`));

  incomeTotals = computed(() => this.columns().map(column => column.incomeTotal));
  expenseTotals = computed(() => this.columns().map(column => column.expenseTotal));
  nets = computed(() => this.columns().map(column => column.net));

  readonly monthLabel = monthLabel;

  ngOnInit(): void {
    // Sin fechas el servidor propone el mes en curso hasta hoy y las devuelve para los filtros
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.reportService.getTransactionsByCategory({ from: this.fromDate() || null, to: this.toDate() || null })
      .subscribe({
        next: report => {
          this.report.set(report);
          this.fromDate.set(report.from);
          this.toDate.set(report.to);
          this.isLoading.set(false);
        },
        error: err => {
          this.report.set(null);
          this.errorMessage.set(err?.error?.message || 'Error al cargar el reporte. Intente nuevamente.');
          this.isLoading.set(false);
        }
      });
  }

  onFromDate(value: string): void {
    this.fromDate.set(value);
    this.load();
  }

  onToDate(value: string): void {
    this.toDate.set(value);
    this.load();
  }

  private lines<T extends { amount: number }>(rows: T[], of: (m: TransactionsByCategoryMonth) => T[],
                                              key: (row: T) => string, label: (row: T) => string): ReportLine[] {
    const columns = this.columns();
    return rows.map(row => ({
      key: key(row),
      label: label(row),
      amounts: columns.map(column => of(column).find(r => key(r) === key(row))?.amount ?? null)
    }));
  }
}
