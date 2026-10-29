import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FinanceSummaryComponent } from './finance-summary.component';
import { FinanceSummaryService } from '../../../core/finance/finance-summary.service';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FinancialSummary } from '../../../core/finance/models/finance.models';

describe('FinanceSummaryComponent', () => {
  let component: FinanceSummaryComponent;
  let fixture: ComponentFixture<FinanceSummaryComponent>;
  let mockSummaryService: any;

  const mockSummaryData: FinancialSummary = {
    totalBalance: 3200,
    cashBalance: 500,
    qrAccountId: 'acc-bisa',
    qrAccountName: 'Cuenta bancaria BISA',
    accounts: [
      {
        id: 'acc-caja',
        name: 'Caja',
        color: '#164312',
        initialBalance: 0,
        currentBalance: 500,
        isCashAccount: true,
        isQrAccount: false
      },
      {
        id: 'acc-bisa',
        name: 'Cuenta bancaria BISA',
        color: '#0284c7',
        initialBalance: 1000,
        currentBalance: 1700,
        isCashAccount: false,
        isQrAccount: true
      }
    ],
    recentTransactions: [
      {
        id: 'tx-1',
        type: 'VENTA_INGRESO',
        amount: 250,
        transactionDate: '2026-10-07T16:00:00',
        description: 'Venta POS #102',
        status: 'ACTIVO',
        sourceAccount: { id: 'acc-caja', name: 'Caja', color: '#164312' }
      }
    ]
  };

  beforeEach(async () => {
    mockSummaryService = {
      getSummary: vi.fn().mockReturnValue(of(mockSummaryData))
    };

    await TestBed.configureTestingModule({
      imports: [FinanceSummaryComponent],
      providers: [
        provideRouter([]),
        { provide: FinanceSummaryService, useValue: mockSummaryService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(FinanceSummaryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse y cargar el resumen financiero consolidado', () => {
    expect(component).toBeTruthy();
    expect(mockSummaryService.getSummary).toHaveBeenCalledTimes(1);
    expect(component.summary()).toEqual(mockSummaryData);
    expect(component.summary()?.totalBalance).toBe(3200);
    expect(component.summary()?.qrAccountName).toBe('Cuenta bancaria BISA');
  });

  it('debe contener las cuentas y transacciones recientes en el resumen', () => {
    const data = component.summary();
    expect(data?.accounts.length).toBe(2);
    expect(data?.recentTransactions.length).toBe(1);
    expect(data?.recentTransactions[0].description).toBe('Venta POS #102');
  });

  it('debe manejar errores de carga del resumen financiero', () => {
    mockSummaryService.getSummary.mockReturnValue(throwError(() => ({ error: { message: 'Fallo de conexión' } })));

    component.loadSummary();

    expect(component.errorMessage()).toBe('Fallo de conexión');
    expect(component.isLoading()).toBe(false);
  });
});
