import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DashboardService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(DashboardService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('DSH-01: sin fechas pide el mes en curso', () => {
    service.getDashboard().subscribe();
    const req = httpTesting.expectOne('/api/dashboard');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({});
  });

  it('DSH-01: envía el período elegido', () => {
    service.getDashboard({ from: '2026-10-26', to: '2026-10-30' }).subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/dashboard');
    expect(req.request.params.get('from')).toBe('2026-10-26');
    expect(req.request.params.get('to')).toBe('2026-10-30');
    req.flush({});
  });
});
