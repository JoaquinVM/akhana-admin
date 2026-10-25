import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PurchaseService } from './purchase.service';
import { PurchaseRequest } from './models/purchase.models';

describe('PurchaseService', () => {
  let service: PurchaseService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PurchaseService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PurchaseService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('COM-04: lista pedidos sin filtros', () => {
    service.getPurchases().subscribe(list => expect(list).toEqual([]));
    const req = httpTesting.expectOne('/api/purchases');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('COM-04: envía proveedor, estado de pago y búsqueda recortada', () => {
    service.getPurchases({ supplierId: 'sup-1', paymentStatus: 'A_CUENTA', search: '  PED  ' }).subscribe();
    const req = httpTesting.expectOne(r => r.url === '/api/purchases');
    expect(req.request.params.get('supplierId')).toBe('sup-1');
    expect(req.request.params.get('paymentStatus')).toBe('A_CUENTA');
    expect(req.request.params.get('search')).toBe('PED');
    req.flush([]);
  });

  it('COM-02: registra un pedido con POST', () => {
    const request: PurchaseRequest = {
      supplierId: 'sup-1',
      purchaseDate: '2026-10-25',
      items: [{ productId: 'p-1', quantity: 1.5, unitCost: 40, updateProductCost: false, newSellPrice: null }],
      shippingCost: 25,
      shippingAccountId: 'acc-1',
      consignment: false,
      comment: null
    };
    service.registerPurchase(request).subscribe();
    const req = httpTesting.expectOne('/api/purchases');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('COM-05: registra un pago del pedido', () => {
    service.registerPayment('pur-1', { accountId: 'acc-1', amount: 100, notes: null }).subscribe();
    const req = httpTesting.expectOne('/api/purchases/pur-1/payments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.amount).toBe(100);
    req.flush({});
  });

  it('COM-05: obtiene las cuentas para pagar desde Compras', () => {
    service.getPaymentAccounts().subscribe();
    httpTesting.expectOne('/api/purchases/payment-accounts').flush([]);
  });

  it('COM-03: pide el precio sugerido al servidor', () => {
    service.getPriceSuggestion('p-1', 12.3).subscribe(s => expect(s.suggestedSellPrice).toBe(19));
    const req = httpTesting.expectOne(r => r.url === '/api/purchases/price-suggestion');
    expect(req.request.params.get('productId')).toBe('p-1');
    expect(req.request.params.get('newCost')).toBe('12.3');
    req.flush({ productId: 'p-1', currentCost: 10, currentSellPrice: 15, currentPercentageProfit: 50, newCost: 12.3, suggestedSellPrice: 19 });
  });
});
