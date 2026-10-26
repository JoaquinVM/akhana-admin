import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CustomerService } from './customer.service';

describe('CustomerService', () => {
  let service: CustomerService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CustomerService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CustomerService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('DEU-07: lista clientes sin filtros y con estado y búsqueda recortada', () => {
    service.getCustomers().subscribe();
    httpTesting.expectOne('/api/customers').flush([]);

    service.getCustomers('ACTIVO', '  neida ').subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/customers');
    expect(req.request.params.get('status')).toBe('ACTIVO');
    expect(req.request.params.get('search')).toBe('neida');
    req.flush([]);
  });

  it('DEU-05: alta, edición y baja de clientes', () => {
    const body = { name: 'Tía Neida', phone: null, description: null };
    service.createCustomer(body).subscribe();
    expect(httpTesting.expectOne('/api/customers').request.method).toBe('POST');

    service.updateCustomer('cus-1', body).subscribe();
    expect(httpTesting.expectOne('/api/customers/cus-1').request.method).toBe('PUT');

    service.deleteCustomer('cus-1').subscribe();
    expect(httpTesting.expectOne('/api/customers/cus-1').request.method).toBe('DELETE');
  });

  it('DEU-06 / DEU-07: historial, cobro y anulación', () => {
    service.getHistory('cus-1').subscribe();
    httpTesting.expectOne('/api/customers/cus-1/history').flush({ customer: null, entries: [] });

    service.registerCollection('cus-1', { accountId: 'acc-1', amount: 50, comment: null }).subscribe();
    const collect = httpTesting.expectOne('/api/customers/cus-1/collections');
    expect(collect.request.method).toBe('POST');
    expect(collect.request.body.amount).toBe(50);
    collect.flush({ customer: null, entries: [] });

    service.voidCollection('cus-1', 'col-1', { voidReason: 'Error' }).subscribe();
    const voided = httpTesting.expectOne('/api/customers/cus-1/collections/col-1/void');
    expect(voided.request.body).toEqual({ voidReason: 'Error' });
    voided.flush({ customer: null, entries: [] });
  });
});
