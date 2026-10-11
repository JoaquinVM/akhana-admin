import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FinancialAccountService } from './financial-account.service';
import { FinancialCategoryService } from './financial-category.service';
import { FinancialTransactionService } from './financial-transaction.service';
import { FinanceSummaryService } from './finance-summary.service';
import {
  FinancialAccount,
  FinancialCategory,
  FinancialTransaction,
  FinancialSummary
} from './models/finance.models';

describe('Finance Core Services', () => {
  let httpTesting: HttpTestingController;
  let accountService: FinancialAccountService;
  let categoryService: FinancialCategoryService;
  let txService: FinancialTransactionService;
  let summaryService: FinanceSummaryService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        FinancialAccountService,
        FinancialCategoryService,
        FinancialTransactionService,
        FinanceSummaryService
      ]
    });

    httpTesting = TestBed.inject(HttpTestingController);
    accountService = TestBed.inject(FinancialAccountService);
    categoryService = TestBed.inject(FinancialCategoryService);
    txService = TestBed.inject(FinancialTransactionService);
    summaryService = TestBed.inject(FinanceSummaryService);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('FinancialAccountService - getAccounts debe obtener cuentas y actualizar signal', () => {
    const mockAccounts: FinancialAccount[] = [
      {
        id: 'acc-1',
        name: 'Cuenta BISA',
        color: '#1B3B18',
        initialBalance: 1000,
        currentBalance: 1500,
        isCashAccount: false,
        isQrAccount: true
      }
    ];

    accountService.getAccounts().subscribe((res) => {
      expect(res.length).toBe(1);
      expect(accountService.accounts().length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/finances/accounts');
    expect(req.request.method).toBe('GET');
    req.flush(mockAccounts);
  });

  it('FinancialCategoryService - getCategories debe filtrar por tipo si se especifica', () => {
    categoryService.getCategories('INGRESO').subscribe();

    const req = httpTesting.expectOne('/api/finances/categories?type=INGRESO');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('FinancialTransactionService - createCashExpense debe enviar POST a /cash-expense', () => {
    txService.createCashExpense({
      amount: 40,
      categoryId: 'cat-1',
      description: 'Cafetería'
    }).subscribe();

    const req = httpTesting.expectOne('/api/finances/transactions/cash-expense');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'tx-1' });
  });

  it('FinanceSummaryService - getSummary debe actualizar el signal summary', () => {
    const mockSummary: FinancialSummary = {
      cashBalance: 500,
      bisaBalance: 1000,
      joacoBalance: 200,
      totalBalance: 1700,
      accounts: [],
      recentTransactions: []
    };

    summaryService.getSummary().subscribe((res) => {
      expect(res.totalBalance).toBe(1700);
      expect(summaryService.summary()?.totalBalance).toBe(1700);
    });

    const req = httpTesting.expectOne('/api/finances/summary');
    expect(req.request.method).toBe('GET');
    req.flush(mockSummary);
  });
});
