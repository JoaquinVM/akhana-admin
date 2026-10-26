import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ConsumptionService } from './consumption.service';

describe('ConsumptionService', () => {
  let service: ConsumptionService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ConsumptionService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ConsumptionService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('CON-01: lista consumos sin filtros', () => {
    service.getConsumptions().subscribe(list => expect(list).toEqual([]));
    const req = httpTesting.expectOne('/api/consumptions');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('CON-01: envía fechas, producto, estado y búsqueda recortada', () => {
    service.getConsumptions({ from: '2026-10-01', to: '2026-10-25', productId: 'p-1', status: 'ANULADO', search: '  Ali ' })
      .subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/consumptions');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-10-25');
    expect(req.request.params.get('productId')).toBe('p-1');
    expect(req.request.params.get('status')).toBe('ANULADO');
    expect(req.request.params.get('search')).toBe('Ali');
    req.flush([]);
  });

  it('CON-01: registra y anula con POST', () => {
    service.registerConsumption({ consumptionDate: null, consumedFor: 'Ali', comment: null,
      items: [{ productId: 'p-1', quantity: 2 }] }).subscribe();
    const create = httpTesting.expectOne('/api/consumptions');
    expect(create.request.method).toBe('POST');
    expect(create.request.body.items).toEqual([{ productId: 'p-1', quantity: 2 }]);
    create.flush({});

    service.voidConsumption('c-1', { voidReason: 'Error' }).subscribe();
    const voidReq = httpTesting.expectOne('/api/consumptions/c-1/void');
    expect(voidReq.request.method).toBe('POST');
    expect(voidReq.request.body).toEqual({ voidReason: 'Error' });
    voidReq.flush({});
  });
});
