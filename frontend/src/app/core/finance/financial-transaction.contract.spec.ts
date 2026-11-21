import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { FinancialTransactionService } from './financial-transaction.service';
import { AccountSummary, CategorySummary, FinancialTransaction } from './models/finance.models';
import transactionResponse from '../../../../../contracts/financial-transaction-response.json';

// Contract with FinancialTransactionResponse (backend ResponseContractTest): the server answers
// contracts/financial-transaction-response.json and its fields must be exactly the model's
const TRANSACTION_FIELDS = {
  id: true, type: true, amount: true, transactionDate: true, operationDate: true, description: true, notes: true,
  status: true, saleId: true, cashSessionId: true, purchaseId: true, creditorRepaymentId: true,
  customerCollectionId: true, historical: true, sourceAccount: true, destinationAccount: true, category: true,
  createdBy: true, createdAt: true, updatedBy: true, updatedAt: true, voidedBy: true, voidedAt: true, voidReason: true,
} satisfies Record<keyof FinancialTransaction, true>;

const ACCOUNT_FIELDS = { id: true, name: true, color: true, cashAccount: true } satisfies Record<keyof AccountSummary, true>;
const CATEGORY_FIELDS = { id: true, name: true, type: true, color: true } satisfies Record<keyof CategorySummary, true>;

const fields = (value: object) => Object.keys(value).sort();

describe('Contrato de FinancialTransactionResponse', () => {
  let service: FinancialTransactionService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(FinancialTransactionService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('el movimiento del servidor trae exactamente los campos del modelo, con sus cuentas y su categoría', () => {
    let tx: FinancialTransaction | undefined;
    service.getTransactionById(transactionResponse.id).subscribe((result) => (tx = result));
    httpTesting.expectOne(`/api/finances/transactions/${transactionResponse.id}`).flush(transactionResponse);

    expect(fields(tx!)).toEqual(fields(TRANSACTION_FIELDS));
    expect(fields(tx!.sourceAccount)).toEqual(fields(ACCOUNT_FIELDS));
    expect(fields(tx!.destinationAccount!)).toEqual(fields(ACCOUNT_FIELDS));
    expect(fields(tx!.category!)).toEqual(fields(CATEGORY_FIELDS));
    expect(tx!.amount).toBe(150);
    expect(tx!.sourceAccount.cashAccount).toBe(true);
  });
});
