import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReportService } from './report.service';

describe('ReportService', () => {
  let service: ReportService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ReportService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ReportService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('FIN-10: pide las ventas por día sin fechas para recibir el mes en curso', () => {
    service.getSalesByDay().subscribe();
    const req = httpTesting.expectOne('/api/reports/sales-by-day');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({});
  });

  it('FIN-10: envía el rango elegido', () => {
    service.getSalesByDay({ from: '2026-07-01', to: '2026-07-31' }).subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/reports/sales-by-day');
    expect(req.request.params.get('from')).toBe('2026-07-01');
    expect(req.request.params.get('to')).toBe('2026-07-31');
    req.flush({});
  });

  it('FIN-10: pide las ventas por proveedor con el rango elegido', () => {
    service.getSalesBySupplier({ from: '2026-07-01', to: '2026-09-30' }).subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/reports/sales-by-supplier');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-07-01');
    expect(req.request.params.get('to')).toBe('2026-09-30');
    req.flush({});
  });

  it('FIN-10: pide los movimientos por categoría con el rango elegido', () => {
    service.getTransactionsByCategory({ from: '2026-08-01', to: '2026-08-31' }).subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/reports/transactions-by-category');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-08-01');
    expect(req.request.params.get('to')).toBe('2026-08-31');
    req.flush({});
  });

  it('FIN-10: pide los totales con el rango elegido', () => {
    service.getTotals({ from: '2026-07-01', to: '2026-07-31' }).subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/reports/totals');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-07-01');
    expect(req.request.params.get('to')).toBe('2026-07-31');
    req.flush({});
  });

  it('CON-03: pide los consumos al costo con el rango elegido', () => {
    service.getConsumptions({ from: '2026-07-01', to: '2026-07-31' }).subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/reports/consumptions');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-07-01');
    expect(req.request.params.get('to')).toBe('2026-07-31');
    req.flush({});
  });

  it('FIN-10: exporta el reporte a Excel con el rango y toma el nombre del archivo del servidor', () => {
    let fileName = '';
    service.exportReport('sales-by-day', { from: '2026-07-01', to: '2026-07-31' })
      .subscribe(file => fileName = file.fileName);
    const req = httpTesting.expectOne(r => r.url === '/api/reports/sales-by-day/export');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('from')).toBe('2026-07-01');
    expect(req.request.params.get('to')).toBe('2026-07-31');
    req.flush(new Blob(['xlsx']), {
      headers: { 'Content-Disposition': 'attachment; filename="akhana-sales-by-day-2026-07-01-2026-07-31.xlsx"' }
    });
    expect(fileName).toBe('akhana-sales-by-day-2026-07-01-2026-07-31.xlsx');
  });

  it('FIN-10: al exportar, el error del servidor llega con su mensaje aunque venga como Blob', async () => {
    const error = new Promise<any>(resolve =>
      service.exportReport('totals', { from: '2026-08-31', to: '2026-07-01' }).subscribe({ error: resolve }));
    httpTesting.expectOne(r => r.url === '/api/reports/totals/export').flush(
      new Blob([JSON.stringify({ message: 'La fecha desde no puede ser posterior a la fecha hasta.' })]),
      { status: 400, statusText: 'Bad Request' });
    expect((await error).error.message).toBe('La fecha desde no puede ser posterior a la fecha hasta.');
  });
});
