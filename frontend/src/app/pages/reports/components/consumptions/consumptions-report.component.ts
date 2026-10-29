import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportExportButtonComponent } from '../export-button/report-export-button.component';
import { ReportService } from '../../../../core/report/report.service';
import { ConsumptionsReport } from '../../../../core/report/models/report.models';
import { monthLabel } from '../../../../core/report/report-labels';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

// CON-03: active consumptions at cost by day and month, and by the product's current supplier and month
@Component({
  selector: 'app-consumptions-report',
  standalone: true,
  imports: [IconComponent, CommonModule, FormsModule, ReportExportButtonComponent],
  templateUrl: './consumptions-report.component.html',
  styleUrls: [
    '../sales-by-day/sales-by-day-report.component.css',
    '../sales-by-supplier/sales-by-supplier-report.component.css',
    './consumptions-report.component.css'
  ]
})
export class ConsumptionsReportComponent implements OnInit {
  private readonly reportService = inject(ReportService);

  report = signal<ConsumptionsReport | null>(null);
  fromDate = signal<string>('');
  toDate = signal<string>('');
  search = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // The search only filters the suppliers on screen; the range total is still that of all
  suppliers = computed(() => {
    const term = this.search().trim().toLowerCase();
    const suppliers = this.report()?.suppliers ?? [];
    return term ? suppliers.filter(s => s.supplierName.toLowerCase().includes(term)) : suppliers;
  });

  readonly monthLabel = monthLabel;

  ngOnInit(): void {
    // Without dates the server proposes the current month up to today and returns them for the filters
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.reportService.getConsumptions({ from: this.fromDate() || null, to: this.toDate() || null }).subscribe({
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

  dayLabel(date: string | null): string {
    if (!date) {
      return '';
    }
    const [year, month, day] = date.split('-');
    return `${day}/${month}/${year}`;
  }
}
