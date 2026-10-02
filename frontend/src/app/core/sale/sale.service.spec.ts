import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SaleService } from './sale.service';
import { CashService } from '../cash/cash.service';
import { Sale, SaleDetailRequest, VoidSaleRequest } from './models/sale.models';
import { of } from 'rxjs';

describe('SaleService', () => {
  let service: SaleService;
  let httpTesting: HttpTestingController;
  let cashServiceMock: any;

  const mockSale: Sale = {
    id: 'sale-1',
    saleNumber: 'VTA-0001-001',
    cashSessionId: 'session-1',
    status: 'COMPLETADA',
    subtotalAmount: 50.0,
    discountItemsTotal: 0.0,
    globalDiscountAmount: 0.0,
    discountTotal: 0.0,
    totalAmount: 50.0,
    paymentMethod: 'EFECTIVO',
    amountCash: 50.0,
    amountQr: 0.0,
    amountReceived: 50.0,
    changeGiven: 0.0,
    description: 'Venta test',
    createdBy: 'cajero',
    createdAt: '2026-10-02T12:00:00Z',
    items: []
  };

  beforeEach(() => {
    cashServiceMock = {
      getCurrentSession: vi.fn().mockReturnValue(of({ id: 'session-1' }))
    };

    TestBed.configureTestingModule({
      providers: [
        SaleService,
        { provide: CashService, useValue: cashServiceMock },
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(SaleService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('debe registrar una venta y añadirla al signal de sales', () => {
    const request: SaleDetailRequest = {
      items: [{ productId: 'prod-1', quantity: 1, discountPerUnit: 0 }],
      paymentMethod: 'EFECTIVO',
      amountCash: 50.0,
      amountReceived: 50.0
    };

    service.registerSale(request).subscribe((sale) => {
      expect(sale.saleNumber).toBe('VTA-0001-001');
      expect(service.sales().length).toBe(1);
      expect(cashServiceMock.getCurrentSession).toHaveBeenCalled();
    });

    const req = httpTesting.expectOne('/api/sales');
    expect(req.request.method).toBe('POST');
    req.flush(mockSale);
  });

  it('debe anular una venta y actualizar su estado en el signal', () => {
    service.sales.set([mockSale]);
    const voidReq: VoidSaleRequest = { voidReason: 'Error de cobro' };
    const voidedSale: Sale = { ...mockSale, status: 'ANULADA', voidReason: 'Error de cobro' };

    service.voidSale('sale-1', voidReq).subscribe((sale) => {
      expect(sale.status).toBe('ANULADA');
      expect(service.sales()[0].status).toBe('ANULADA');
      expect(cashServiceMock.getCurrentSession).toHaveBeenCalled();
    });

    const req = httpTesting.expectOne('/api/sales/sale-1/void');
    expect(req.request.method).toBe('POST');
    req.flush(voidedSale);
  });

  it('debe cargar las ventas de una sesión', () => {
    service.getSalesBySession('session-1').subscribe((sales) => {
      expect(sales.length).toBe(1);
      expect(service.sales().length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/sales/session/session-1');
    expect(req.request.method).toBe('GET');
    req.flush([mockSale]);
  });
});
