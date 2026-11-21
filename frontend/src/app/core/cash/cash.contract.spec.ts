import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CashService } from './cash.service';
import { CashSessionSummary } from './models/cash.models';
import cashSessionResponse from '../../../../../contracts/cash-session-response.json';

// Contract with CashSessionSummaryResponse (backend ResponseContractTest): the server answers
// contracts/cash-session-response.json and its fields must be exactly the model's
const CASH_SESSION_FIELDS = {
  id: true, sessionNumber: true, status: true, openingAmount: true, openingComment: true, openedBy: true,
  openedAt: true, closingAmount: true, closingComment: true, closedBy: true, closedAt: true, totalSalesCash: true,
  totalSalesQr: true, totalSales: true, expectedCash: true, difference: true, salesCount: true, historical: true,
  operationDate: true, reopenable: true,
} satisfies Record<keyof CashSessionSummary, true>;

const fields = (value: object) => Object.keys(value).sort();

describe('Contrato de CashSessionSummaryResponse', () => {
  let service: CashService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CashService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('la caja del servidor trae exactamente los campos del modelo CashSessionSummary', () => {
    service.getCurrentSession().subscribe();
    httpTesting.expectOne('/api/cash-sessions/current').flush(cashSessionResponse);

    const session = service.currentSession()!;
    expect(fields(session)).toEqual(fields(CASH_SESSION_FIELDS));
    expect(session.expectedCash).toBe(245);
    expect(session.operationDate).toBe('2026-11-30');
  });
});
