import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SalesByDayReportComponent } from './sales-by-day-report.component';
import { ReportService } from '../../../../core/report/report.service';
import { SalesByDayReport, SalesByDayRow } from '../../../../core/report/models/report.models';

describe('SalesByDayReportComponent', () => {
  let component: SalesByDayReportComponent;
  let fixture: ComponentFixture<SalesByDayReportComponent>;
  let reportService: any;

  const row = (date: string | null, overrides: Partial<SalesByDayRow> = {}): SalesByDayRow => ({
    date, normalSales: 30, normalQuantity: 74, normalTotal: 2745, normalCost: 2104.16, normalProfit: 640.84,
    normalMargin: 23.35, atCostSales: 1, atCostQuantity: 1, atCostTotal: 104, total: 2849, profit: 640.84,
    ...overrides
  });
  const report: SalesByDayReport = {
    from: '2026-07-01', to: '2026-07-31',
    months: [{ month: '2026-07', days: [row('2026-07-17')], totals: row(null) }],
    totals: row(null)
  };

  beforeEach(async () => {
    reportService = { getSalesByDay: vi.fn().mockReturnValue(of(report)) };
    await TestBed.configureTestingModule({
      imports: [SalesByDayReportComponent],
      providers: [{ provide: ReportService, useValue: reportService }]
    }).compileComponents();

    fixture = TestBed.createComponent(SalesByDayReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('FIN-10: carga el mes en curso sin fechas y llena los filtros con el rango del servidor', () => {
    expect(reportService.getSalesByDay).toHaveBeenCalledWith({ from: null, to: null });
    expect(component.fromDate()).toBe('2026-07-01');
    expect(component.toDate()).toBe('2026-07-31');
  });

  it('CON-03: muestra normales y al costo aparte, con subtotal del mes y total del rango', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Ventas normales');
    expect(text).toContain('Ventas al costo');
    expect(text).toContain('17/07/2026');
    expect(text).toContain('2,745.00');
    expect(text).toContain('640.84');
    expect(text).toContain('23.35 %');
    expect(text).toContain('Bs 2,849.00');
    expect(text).toContain('Total julio 2026');
    expect(text).toContain('Total del rango');
  });

  it('FIN-10: muestra el margen vacío si no hay ventas normales', () => {
    reportService.getSalesByDay.mockReturnValue(of({
      ...report, months: [{ month: '2026-07', days: [row('2026-07-17', { normalMargin: null })], totals: row(null) }]
    }));
    component.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('—');
  });

  it('FIN-10: al cambiar una fecha vuelve a pedir el reporte con el rango', () => {
    component.onFromDate('2026-07-15');
    expect(reportService.getSalesByDay).toHaveBeenLastCalledWith({ from: '2026-07-15', to: '2026-07-31' });
  });

  it('FIN-10: avisa cuando no hay ventas y muestra el error del servidor', () => {
    reportService.getSalesByDay.mockReturnValue(of({ ...report, months: [] }));
    component.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay ventas');

    reportService.getSalesByDay.mockReturnValue(
      throwError(() => ({ error: { message: 'La fecha desde no puede ser posterior a la fecha hasta.' } })));
    component.onFromDate('2026-08-10');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('La fecha desde no puede ser posterior a la fecha hasta.');
  });
});
