import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CreditorService } from './creditor.service';

describe('CreditorService', () => {
  let service: CreditorService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CreditorService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CreditorService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('DEU-01: lista personas sin filtros y con estado y búsqueda recortada', () => {
    service.getCreditors().subscribe();
    httpTesting.expectOne('/api/creditors').flush([]);

    service.getCreditors('ACTIVO', '  mami ').subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/creditors');
    expect(req.request.params.get('status')).toBe('ACTIVO');
    expect(req.request.params.get('search')).toBe('mami');
    req.flush([]);
  });

  it('DEU-04: lee el historial de una persona', () => {
    service.getHistory('cre-1').subscribe();
    const req = httpTesting.expectOne('/api/creditors/cre-1/history');
    expect(req.request.method).toBe('GET');
    req.flush({ creditor: null, entries: [] });
  });

  it('DEU-03: registra y anula devoluciones con POST', () => {
    service.registerRepayment('cre-1', { accountId: 'acc-1', amount: 150, comment: null }).subscribe();
    const post = httpTesting.expectOne('/api/creditors/cre-1/repayments');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ accountId: 'acc-1', amount: 150, comment: null });
    post.flush({});

    service.voidRepayment('cre-1', 'rep-1', { voidReason: 'Error' }).subscribe();
    const voidReq = httpTesting.expectOne('/api/creditors/cre-1/repayments/rep-1/void');
    expect(voidReq.request.method).toBe('POST');
    voidReq.flush({});
  });

  it('lee las cuentas para devolver sin pasar por Finanzas', () => {
    service.getAccounts().subscribe();
    httpTesting.expectOne('/api/debts/accounts').flush([]);
  });
});
