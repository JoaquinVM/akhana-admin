import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CashService } from './cash.service';
import {
  CashSessionSummary,
  CashSessionDetail,
  OpenCashRequest,
  CloseCashRequest,
  SaleRequest,
  Sale
} from './models/cash.models';

describe('CashService', () => {
  let service: CashService;
  let httpTesting: HttpTestingController;

  const mockSession: CashSessionSummary = {
    id: 'session-1',
    sessionNumber: 1,
    status: 'ABIERTA',
    openingAmount: 150.0,
    openingComment: 'Fondo inicial',
    openedBy: 'admin',
    openedAt: '2026-10-01T10:00:00Z',
    closingAmount: null,
    closingComment: null,
    closedBy: null,
    closedAt: null,
    totalSalesCash: 50.0,
    totalSalesQr: 30.0,
    totalSales: 80.0,
    expectedCash: 200.0,
    difference: null,
    salesCount: 2
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CashService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(CashService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('debe obtener la sesión actual y actualizar el signal currentSession', () => {
    service.getCurrentSession().subscribe((session) => {
      expect(session.status).toBe('ABIERTA');
      expect(session.openingAmount).toBe(150.0);
    });

    const req = httpTesting.expectOne('/api/cash-sessions/current');
    expect(req.request.method).toBe('GET');
    req.flush(mockSession);

    expect(service.currentSession()).toEqual(mockSession);
  });

  it('debe abrir una nueva sesión y actualizar currentSession', () => {
    const request: OpenCashRequest = { openingAmount: 200.0, openingComment: 'Inicio turno' };

    service.openSession(request).subscribe((session) => {
      expect(session.status).toBe('ABIERTA');
      expect(service.currentSession()).toEqual(session);
    });

    const req = httpTesting.expectOne('/api/cash-sessions/open');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({ ...mockSession, openingAmount: 200.0 });
  });

  it('debe cerrar la sesión actual y reiniciar currentSession a null', () => {
    service.currentSession.set(mockSession);
    const request: CloseCashRequest = { closingAmount: 200.0, closingComment: 'Cierre ok' };

    service.closeSession(request).subscribe();

    const req = httpTesting.expectOne('/api/cash-sessions/current/close');
    expect(req.request.method).toBe('POST');
    req.flush({ ...mockSession, status: 'CERRADA', closingAmount: 200.0 });

    expect(service.currentSession()).toBeNull();
  });

  it('debe registrar una venta y refrescar la sesión activa', () => {
    const saleReq: SaleRequest = { totalAmount: 45.0, paymentMethod: 'EFECTIVO' };
    const mockSale: Sale = {
      id: 'sale-1',
      saleNumber: 'VTA-0001-001',
      cashSessionId: 'session-1',
      totalAmount: 45.0,
      paymentMethod: 'EFECTIVO',
      description: null,
      createdBy: 'admin',
      createdAt: '2026-10-01T11:00:00Z'
    };

    service.registerSale(saleReq).subscribe((res) => {
      expect(res.saleNumber).toBe('VTA-0001-001');
    });

    const saleHttp = httpTesting.expectOne('/api/cash-sessions/current/sales');
    expect(saleHttp.request.method).toBe('POST');
    saleHttp.flush(mockSale);

    // Debe invocar refresh de la sesión actual
    const refreshHttp = httpTesting.expectOne('/api/cash-sessions/current');
    expect(refreshHttp.request.method).toBe('GET');
    refreshHttp.flush(mockSession);
  });

  it('debe obtener el historial de sesiones', () => {
    service.getAllSessions().subscribe((list) => {
      expect(list.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/cash-sessions');
    expect(req.request.method).toBe('GET');
    req.flush([mockSession]);
  });
});
