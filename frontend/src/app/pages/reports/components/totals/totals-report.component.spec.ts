import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TotalsReportComponent } from './totals-report.component';
import { ReportService } from '../../../../core/report/report.service';
import { TotalsReport, TotalsRow } from '../../../../core/report/models/report.models';

describe('TotalsReportComponent', () => {
  let component: TotalsReportComponent;
  let fixture: ComponentFixture<TotalsReportComponent>;
  let reportService: any;

  const row = (month: string | null, overrides: Partial<TotalsRow> = {}): TotalsRow => ({
    month, normalSales: 430, normalTotal: 37584, normalCost: 29078.22, normalProfit: 8505.78, normalMargin: 22.63,
    atCostSales: 1, atCostTotal: 104, pendingTotal: 0, thirdPartyTotal: 0, total: 37688, consumptions: 3, consumptionTotal: 300,
    profitAfterConsumptions: 8205.78, ...overrides
  });
  const report: TotalsReport = {
    from: '2026-07-01', to: '2026-07-31', months: [row('2026-07')], totals: row(null)
  };

  beforeEach(async () => {
    reportService = { getTotals: vi.fn().mockReturnValue(of(report)) };
    await TestBed.configureTestingModule({
      imports: [TotalsReportComponent],
      providers: [{ provide: ReportService, useValue: reportService }]
    }).compileComponents();

    fixture = TestBed.createComponent(TotalsReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('FIN-10: carga el mes en curso sin fechas y llena los filtros con el rango del servidor', () => {
    expect(reportService.getTotals).toHaveBeenCalledWith({ from: null, to: null });
    expect(component.fromDate()).toBe('2026-07-01');
    expect(component.toDate()).toBe('2026-07-31');
  });

  it('CON-03: julio muestra la utilidad 8505.78, los consumos al costo y la utilidad menos consumos', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('julio 2026');
    expect(text).toContain('8,505.78');
    expect(text).toContain('22.63 %');
    expect(text).toContain('Bs 37,688.00');
    expect(text).toContain('Consumos al costo');
    expect(text).toContain('300.00');
    expect(text).toContain('Bs 8,205.78');
    expect(text).toContain('Total del rango');
  });

  it('FIN-10: un mes solo con consumos muestra el margen vacío', () => {
    reportService.getTotals.mockReturnValue(of({
      ...report, months: [row('2026-08', { normalSales: 0, normalMargin: null })]
    }));
    component.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('—');
  });

  it('FIN-10: al cambiar una fecha vuelve a pedir el reporte con el rango', () => {
    component.onToDate('2026-09-30');
    expect(reportService.getTotals).toHaveBeenLastCalledWith({ from: '2026-07-01', to: '2026-09-30' });
  });

  it('FIN-10: avisa cuando no hay datos y muestra el error del servidor', () => {
    reportService.getTotals.mockReturnValue(of({ ...report, months: [] }));
    component.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay ventas ni consumos');

    reportService.getTotals.mockReturnValue(
      throwError(() => ({ error: { message: 'El rango del reporte no puede superar un año.' } })));
    component.onFromDate('2025-01-01');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('El rango del reporte no puede superar un año.');
  });
});
