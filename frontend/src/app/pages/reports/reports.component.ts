import { Component, signal } from '@angular/core';
import { ConsumptionsReportComponent } from './components/consumptions/consumptions-report.component';
import { SalesByDayReportComponent } from './components/sales-by-day/sales-by-day-report.component';
import { TotalsReportComponent } from './components/totals/totals-report.component';
import { SalesBySupplierReportComponent } from './components/sales-by-supplier/sales-by-supplier-report.component';
import {
  TransactionsByCategoryReportComponent
} from './components/transactions-by-category/transactions-by-category-report.component';

export type ReportTab = 'SALES_BY_DAY' | 'SALES_BY_SUPPLIER' | 'TRANSACTIONS_BY_CATEGORY' | 'TOTALS'
  | 'CONSUMPTIONS';

// FIN-10: cada reporte es una pestaña con su propio filtro de fechas (specs/reports.md)
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    SalesByDayReportComponent, SalesBySupplierReportComponent, TransactionsByCategoryReportComponent,
    TotalsReportComponent, ConsumptionsReportComponent
  ],
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.css']
})
export class ReportsComponent {
  readonly tabs: { value: ReportTab; label: string }[] = [
    { value: 'SALES_BY_DAY', label: 'Ventas por día' },
    { value: 'SALES_BY_SUPPLIER', label: 'Ventas por proveedor' },
    { value: 'TRANSACTIONS_BY_CATEGORY', label: 'Movimientos por categoría' },
    { value: 'TOTALS', label: 'Totales' },
    { value: 'CONSUMPTIONS', label: 'Consumos' }
  ];

  activeTab = signal<ReportTab>('SALES_BY_DAY');
}
