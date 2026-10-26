import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReportExportButtonComponent } from '../export-button/report-export-button.component';
import { ReportService } from '../../../../core/report/report.service';
import { SalesBySupplierReport } from '../../../../core/report/models/report.models';
import { monthLabel } from '../../../../core/report/report-labels';

// FIN-10 / CON-03: ventas por proveedor actual del producto y mes; reemplaza Resumen Ventas
@Component({
  selector: 'app-sales-by-supplier-report',
  standalone: true,
  imports: [CommonModule, FormsModule, ReportExportButtonComponent],
  templateUrl: './sales-by-supplier-report.component.html',
  styleUrls: ['../sales-by-day/sales-by-day-report.component.css', './sales-by-supplier-report.component.css']
})
export class SalesBySupplierReportComponent implements OnInit {
  private readonly reportService = inject(ReportService);

  report = signal<SalesBySupplierReport | null>(null);
  fromDate = signal<string>('');
  toDate = signal<string>('');
  search = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  // La búsqueda solo filtra la vista; el total del rango sigue siendo el de todos los proveedores
  suppliers = computed(() => {
    const term = this.search().trim().toLowerCase();
    const suppliers = this.report()?.suppliers ?? [];
    return term ? suppliers.filter(s => s.supplierName.toLowerCase().includes(term)) : suppliers;
  });

  readonly monthLabel = monthLabel;

  ngOnInit(): void {
    // Sin fechas el servidor propone el mes en curso hasta hoy y las devuelve para los filtros
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.reportService.getSalesBySupplier({ from: this.fromDate() || null, to: this.toDate() || null }).subscribe({
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
}
