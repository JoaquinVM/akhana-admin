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
});
