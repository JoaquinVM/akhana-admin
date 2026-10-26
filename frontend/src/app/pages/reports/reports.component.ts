import { Component, signal } from '@angular/core';
import { SalesByDayReportComponent } from './components/sales-by-day/sales-by-day-report.component';
import { SalesBySupplierReportComponent } from './components/sales-by-supplier/sales-by-supplier-report.component';

export type ReportTab = 'SALES_BY_DAY' | 'SALES_BY_SUPPLIER';

// FIN-10: cada reporte es una pestaña con su propio filtro de fechas (specs/reports.md)
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [SalesByDayReportComponent, SalesBySupplierReportComponent],
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.css']
})
export class ReportsComponent {
  readonly tabs: { value: ReportTab; label: string }[] = [
    { value: 'SALES_BY_DAY', label: 'Ventas por día' },
    { value: 'SALES_BY_SUPPLIER', label: 'Ventas por proveedor' }
  ];

  activeTab = signal<ReportTab>('SALES_BY_DAY');
}
