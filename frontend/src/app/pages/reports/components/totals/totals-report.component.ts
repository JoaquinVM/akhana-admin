import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportExportButtonComponent } from '../export-button/report-export-button.component';
import { ReportService } from '../../../../core/report/report.service';
import { TotalsReport } from '../../../../core/report/models/report.models';
import { monthLabel } from '../../../../core/report/report-labels';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

// FIN-10 / CON-03: sales and profit totals per month, with consumptions at cost and profit minus consumptions
@Component({
  selector: 'app-totals-report',
  standalone: true,
  imports: [IconComponent, CommonModule, FormsModule, ReportExportButtonComponent],
  templateUrl: './totals-report.component.html',
  styleUrls: ['./totals-report.component.css']
})
export class TotalsReportComponent implements OnInit {
  private readonly reportService = inject(ReportService);

  report = signal<TotalsReport | null>(null);
  fromDate = signal<string>('');
  toDate = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    // Without dates the server proposes the current month up to today and returns them for the filters
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.reportService.getTotals({ from: this.fromDate() || null, to: this.toDate() || null }).subscribe({
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

  readonly monthLabel = monthLabel;
}
