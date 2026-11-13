import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CashService } from './cash.service';
import { CloseCashDraft, CloseCashDraftService } from './close-cash-draft.service';
import {
  CashSessionSummary,
  CashSessionDetail,
  OpenCashRequest,
  CloseCashRequest
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

  it('debe obtener el monto inicial propuesto (CAJ-10)', () => {
    service.getOpeningProposal().subscribe((proposal) => {
      expect(proposal.proposedAmount).toBe(245.5);
      expect(proposal.previousSessionNumber).toBe(7);
    });

    const req = httpTesting.expectOne('/api/cash-sessions/opening-proposal');
    expect(req.request.method).toBe('GET');
    req.flush({ proposedAmount: 245.5, previousSessionNumber: 7 });
  });

  it('debe obtener la sesión actual y actualizar el signal currentSession', () => {
    service.getCurrentSession().subscribe((session) => {
      expect(session?.status).toBe('ABIERTA');
      expect(session?.openingAmount).toBe(150.0);
    });

    const req = httpTesting.expectOne('/api/cash-sessions/current');
    expect(req.request.method).toBe('GET');
    req.flush(mockSession);

    expect(service.currentSession()).toEqual(mockSession);
  });

  it('sin caja abierta el servidor responde 204 y currentSession queda en null', () => {
    service.currentSession.set(mockSession);
    let result: unknown = 'sin respuesta';
    service.getCurrentSession().subscribe((session) => (result = session));

    httpTesting.expectOne('/api/cash-sessions/current').flush(null, { status: 204, statusText: 'No Content' });

    expect(result).toBeNull();
    expect(service.currentSession()).toBeNull();
    expect(service.isLoadingCurrent()).toBe(false);
  });

  describe('POS-17: una falla de conexión no cierra la caja', () => {
    const failCheck = (status: number) => {
      service.getCurrentSession().subscribe({ error: () => undefined });
      httpTesting.expectOne('/api/cash-sessions/current').flush(null, { status, statusText: 'Falla' });
    };

    it.each([0, 500, 502, 504])('con %i mantiene la caja conocida y lo avisa', (status) => {
      service.currentSession.set(mockSession);
      failCheck(status);
      expect(service.currentSession()).toEqual(mockSession);
      expect(service.sessionCheckFailed()).toBe(true);
      expect(service.isLoadingCurrent()).toBe(false);
    });

    it('sin caja conocida tampoco la da por cerrada: avisa que no sabe', () => {
      failCheck(503);
      expect(service.currentSession()).toBeNull();
      expect(service.sessionCheckFailed()).toBe(true);
    });

    it.each([401, 403, 404])('con %i el servidor respondió: queda cerrada', (status) => {
      service.currentSession.set(mockSession);
      failCheck(status);
      expect(service.currentSession()).toBeNull();
      expect(service.sessionCheckFailed()).toBe(false);
    });

    it('la siguiente respuesta quita el aviso, y dice la verdad si ya se cerró', () => {
      service.currentSession.set(mockSession);
      failCheck(502);
      service.getCurrentSession().subscribe();
      httpTesting.expectOne('/api/cash-sessions/current').flush(null, { status: 204, statusText: 'No Content' });
      expect(service.currentSession()).toBeNull();
      expect(service.sessionCheckFailed()).toBe(false);
    });

    it('abrir la caja quita el aviso', () => {
      failCheck(502);
      service.openSession({ openingAmount: 200 } as OpenCashRequest).subscribe();
      httpTesting.expectOne('/api/cash-sessions/open').flush(mockSession);
      expect(service.sessionCheckFailed()).toBe(false);
    });
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

  it('CAJ-15: el conteo guardado en el navegador se borra solo cuando la caja se cierra', () => {
    const draft = TestBed.inject(CloseCashDraftService);
    const saved: CloseCashDraft = { sessionId: 'session-1', mode: 'ARQUEO', cuts: [], reserveBills: 150, reserveCoins: 0,
      closingAmount: 150, closingComment: '' };
    draft.save(saved);

    service.closeSession({ closingAmount: 100 }).subscribe({ error: () => undefined });
    httpTesting.expectOne('/api/cash-sessions/current/close')
      .flush({ message: 'Los cortes suman…' }, { status: 400, statusText: 'Bad Request' });
    expect(draft.load('session-1')).toEqual(saved);

    service.closeSession({ closingAmount: 150, reserveBills: 150 }).subscribe();
    const req = httpTesting.expectOne('/api/cash-sessions/current/close');
    expect(req.request.body).toEqual({ closingAmount: 150, reserveBills: 150 });
    req.flush({ ...mockSession, status: 'CERRADA', closingAmount: 150 });
    expect(draft.load('session-1')).toBeNull();
  });

  it('debe obtener el historial de sesiones', () => {
    service.getAllSessions().subscribe((list) => {
      expect(list.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/cash-sessions');
    expect(req.request.method).toBe('GET');
    req.flush([mockSession]);
  });

  it('CAJ-14: reabrir envía el motivo y deja la caja como actual, para que el POS y el menú cambien', () => {
    service.reopenSession('session-1', 'Cerrada por error').subscribe((session) => {
      expect(session.id).toBe('session-1');
    });

    const req = httpTesting.expectOne('/api/cash-sessions/session-1/reopen');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Cerrada por error' });
    req.flush(mockSession);
    expect(service.currentSession()?.id).toBe('session-1');
  });

  it('CAJ-14: si reabrir falla, la caja actual no cambia', () => {
    service.reopenSession('session-1', 'Motivo').subscribe({ error: () => undefined });

    httpTesting.expectOne('/api/cash-sessions/session-1/reopen')
      .flush({ message: 'Solo se puede reabrir la última caja.' }, { status: 400, statusText: 'Bad Request' });
    expect(service.currentSession()).toBeNull();
  });
});
