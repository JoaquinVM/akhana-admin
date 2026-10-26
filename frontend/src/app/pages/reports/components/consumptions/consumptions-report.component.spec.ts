import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ConsumptionsReportComponent } from './consumptions-report.component';
import { ReportService } from '../../../../core/report/report.service';
import { ConsumptionsReport } from '../../../../core/report/models/report.models';

describe('ConsumptionsReportComponent', () => {
  let component: ConsumptionsReportComponent;
  let fixture: ComponentFixture<ConsumptionsReportComponent>;
  let reportService: any;

  const report: ConsumptionsReport = {
    from: '2026-07-01', to: '2026-08-31',
    months: [
      {
        month: '2026-07',
        days: [
          { date: '2026-07-21', consumptions: 2, quantity: 1.1, total: 16 },
          { date: '2026-07-25', consumptions: 1, quantity: 3, total: 30 }
        ],
        totals: { date: null, consumptions: 3, quantity: 4.1, total: 46 }
      }
    ],
    totals: { date: null, consumptions: 3, quantity: 4.1, total: 46 },
    suppliers: [
      {
        supplierId: 'a', supplierName: 'Alma',
        months: [{ month: '2026-07', quantity: 0.1, total: 6 }],
        totals: { month: null, quantity: 0.1, total: 6 }
      },
      {
        supplierId: 'b', supplierName: 'Bohemia',
        months: [{ month: '2026-07', quantity: 4, total: 40 }],
        totals: { month: null, quantity: 4, total: 40 }
      }
    ],
    supplierTotals: { month: null, quantity: 4.1, total: 46 }
  };

  beforeEach(async () => {
    reportService = { getConsumptions: vi.fn().mockReturnValue(of(report)) };
    await TestBed.configureTestingModule({
      imports: [ConsumptionsReportComponent],
      providers: [{ provide: ReportService, useValue: reportService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ConsumptionsReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('CON-03: carga el mes en curso sin fechas y llena los filtros con el rango del servidor', () => {
    expect(reportService.getConsumptions).toHaveBeenCalledWith({ from: null, to: null });
    expect(component.fromDate()).toBe('2026-07-01');
    expect(component.toDate()).toBe('2026-08-31');
  });

  it('CON-03: muestra por día la cantidad de consumos, productos y total al costo, con subtotal del mes', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('21/07/2026');
    expect(text).toContain('1.1');
    expect(text).toContain('Bs 16.00');
    expect(text).toContain('Total julio 2026');
    expect(text).toContain('Bs 46.00');
  });

  it('CON-03: muestra por proveedor y mes la cantidad y el total al costo', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Por proveedor y mes');
    expect(text).toContain('Total Bohemia');
    expect(text).toContain('Bs 40.00');
  });

  it('CON-03: la búsqueda filtra proveedores sin cambiar el total del rango', () => {
    component.search.set('alm');
    fixture.detectChanges();
    expect(component.suppliers().map(s => s.supplierName)).toEqual(['Alma']);
    expect(fixture.nativeElement.textContent).not.toContain('Total Bohemia');

    component.search.set('zzz');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ningún proveedor coincide');
  });

  it('CON-03: sin consumos muestra el estado vacío', () => {
    reportService.getConsumptions.mockReturnValue(of({ ...report, months: [], suppliers: [] }));
    component.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay consumos');
  });

  it('CON-03: al cambiar una fecha vuelve a pedir el reporte y muestra el error del servidor', () => {
    reportService.getConsumptions.mockReturnValue(
      throwError(() => ({ error: { message: 'El rango del reporte no puede superar un año.' } })));
    component.onToDate('2027-09-01');
    fixture.detectChanges();
    expect(reportService.getConsumptions).toHaveBeenLastCalledWith({ from: '2026-07-01', to: '2027-09-01' });
    expect(fixture.nativeElement.textContent).toContain('El rango del reporte no puede superar un año.');
  });
});
