import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportExportButtonComponent } from '../export-button/report-export-button.component';
import { ReportService } from '../../../../core/report/report.service';
import { SalesByDayReport } from '../../../../core/report/models/report.models';
import { monthLabel } from '../../../../core/report/report-labels';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

// FIN-10 / CON-03: sales by store day, with month subtotals; replaces Resumen Ventas Diario
@Component({
  selector: 'app-sales-by-day-report',
  standalone: true,
  imports: [IconComponent, CommonModule, FormsModule, ReportExportButtonComponent],
  templateUrl: './sales-by-day-report.component.html',
  styleUrls: ['./sales-by-day-report.component.css']
})
export class SalesByDayReportComponent implements OnInit {
  private readonly reportService = inject(ReportService);

  report = signal<SalesByDayReport | null>(null);
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
    this.reportService.getSalesByDay({ from: this.fromDate() || null, to: this.toDate() || null }).subscribe({
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

  // Store days as text, without going through the browser's time zone
  dayLabel(date: string | null): string {
    if (!date) {
      return '';
    }
    const [year, month, day] = date.split('-');
    return `${day}/${month}/${year}`;
  }
}
