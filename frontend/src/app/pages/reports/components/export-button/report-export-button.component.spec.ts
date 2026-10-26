import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ReportExportButtonComponent } from './report-export-button.component';
import { ReportService } from '../../../../core/report/report.service';

describe('ReportExportButtonComponent', () => {
  let fixture: ComponentFixture<ReportExportButtonComponent>;
  let reportService: any;

  beforeEach(async () => {
    reportService = {
      exportReport: vi.fn().mockReturnValue(of({
        fileName: 'akhana-totals-2026-07-01-2026-07-31.xlsx', content: new Blob(['xlsx'])
      }))
    };
    await TestBed.configureTestingModule({
      imports: [ReportExportButtonComponent],
      providers: [{ provide: ReportService, useValue: reportService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ReportExportButtonComponent);
    fixture.componentRef.setInput('report', 'totals');
    fixture.componentRef.setInput('from', '2026-07-01');
    fixture.componentRef.setInput('to', '2026-07-31');
    fixture.detectChanges();
    URL.createObjectURL = vi.fn().mockReturnValue('blob:report');
    URL.revokeObjectURL = vi.fn();
  });

  const button = (): HTMLButtonElement => fixture.nativeElement.querySelector('button');

  it('FIN-10: descarga el .xlsx del reporte con el rango de la pestaña y su nombre', () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('akhana-totals-2026-07-01-2026-07-31.xlsx');
    });
    expect(button().textContent).toContain('Exportar a Excel');
    button().click();
    expect(reportService.exportReport).toHaveBeenCalledWith('totals', { from: '2026-07-01', to: '2026-07-31' });
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });

  it('FIN-10: muestra el error del servidor', () => {
    reportService.exportReport.mockReturnValue(throwError(() => ({
      error: { message: 'El rango del reporte no puede superar un año.' }
    })));
    button().click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('El rango del reporte no puede superar un año.');
    expect(button().disabled).toBe(false);
  });

  it('FIN-10: queda deshabilitado mientras carga el reporte', () => {
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(button().disabled).toBe(true);
  });
});
