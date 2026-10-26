import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SalesBySupplierReportComponent } from './sales-by-supplier-report.component';
import { ReportService } from '../../../../core/report/report.service';
import { SalesBySupplierReport, SalesBySupplierRow } from '../../../../core/report/models/report.models';

describe('SalesBySupplierReportComponent', () => {
  let component: SalesBySupplierReportComponent;
  let fixture: ComponentFixture<SalesBySupplierReportComponent>;
  let reportService: any;

  const row = (month: string | null, overrides: Partial<SalesBySupplierRow> = {}): SalesBySupplierRow => ({
    month, normalQuantity: 2, normalTotal: 160, normalCost: 144, normalProfit: 16, normalMargin: 10,
    atCostQuantity: 0, atCostTotal: 0, total: 160, ...overrides
  });
  const report: SalesBySupplierReport = {
    from: '2026-07-01', to: '2026-09-30',
    suppliers: [
      {
        supplierId: 'b', supplierName: 'Bohemia',
        months: [row('2026-07'), row('2026-08'), row('2026-09', { normalQuantity: 3, normalTotal: 230, normalProfit: 28 })],
        totals: row(null, { normalQuantity: 7, normalTotal: 550, normalProfit: 60, normalMargin: 10.91, total: 550 })
      },
      {
        supplierId: 'a', supplierName: 'Alma', months: [row('2026-07', { atCostQuantity: 1, atCostTotal: 104, total: 264 })],
        totals: row(null, { atCostQuantity: 1, atCostTotal: 104, total: 264 })
      }
    ],
    totals: row(null, { total: 814 })
  };

  beforeEach(async () => {
    reportService = { getSalesBySupplier: vi.fn().mockReturnValue(of(report)) };
    await TestBed.configureTestingModule({
      imports: [SalesBySupplierReportComponent],
      providers: [{ provide: ReportService, useValue: reportService }]
    }).compileComponents();

    fixture = TestBed.createComponent(SalesBySupplierReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('FIN-10: carga el mes en curso sin fechas y llena los filtros con el rango del servidor', () => {
    expect(reportService.getSalesBySupplier).toHaveBeenCalledWith({ from: null, to: null });
    expect(component.fromDate()).toBe('2026-07-01');
    expect(component.toDate()).toBe('2026-09-30');
  });

  it('FIN-10: muestra cada proveedor con sus meses y su total, como Resumen Ventas', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Bohemia');
    expect(text).toContain('julio 2026');
    expect(text).toContain('septiembre 2026');
    expect(text).toContain('230.00');
    expect(text).toContain('Total Bohemia');
    expect(text).toContain('550.00');
    expect(text).toContain('10.91 %');
    expect(text).toContain('Total del rango');
    expect(text).toContain('Bs 814.00');
  });

  it('CON-03: muestra las ventas al costo en columnas aparte', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Ventas al costo');
    expect(text).toContain('104.00');
  });

  it('FIN-10: la búsqueda filtra proveedores sin cambiar el total del rango', () => {
    component.search.set('alm');
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(component.suppliers().map(s => s.supplierName)).toEqual(['Alma']);
    expect(text).not.toContain('Total Bohemia');
    expect(text).toContain('Bs 814.00');

    component.search.set('zzz');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ningún proveedor coincide');
  });

  it('FIN-10: al cambiar una fecha vuelve a pedir el reporte y muestra el error del servidor', () => {
    reportService.getSalesBySupplier.mockReturnValue(
      throwError(() => ({ error: { message: 'La fecha desde no puede ser posterior a la fecha hasta.' } })));
    component.onFromDate('2026-10-10');
    fixture.detectChanges();
    expect(reportService.getSalesBySupplier).toHaveBeenLastCalledWith({ from: '2026-10-10', to: '2026-09-30' });
    expect(fixture.nativeElement.textContent).toContain('La fecha desde no puede ser posterior a la fecha hasta.');
  });
});
