import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  CashExpenseRequest,
  CashTransferAccount,
  CashTransferRequest,
  FinancialTransaction,
  FinancialTransactionRequest,
  TransactionFilterParams,
  VoidFinancialTransactionRequest
} from './models/finance.models';

@Injectable({
  providedIn: 'root'
})
export class FinancialTransactionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/finances/transactions';

  transactions = signal<FinancialTransaction[]>([]);
  isLoading = signal<boolean>(false);

  getTransactions(filter?: TransactionFilterParams): Observable<FinancialTransaction[]> {
    this.isLoading.set(true);
    let params = new HttpParams();

    if (filter) {
      if (filter.from) params = params.set('from', filter.from);
      if (filter.to) params = params.set('to', filter.to);
      if (filter.type) params = params.set('type', filter.type);
      if (filter.accountId) params = params.set('accountId', filter.accountId);
      if (filter.categoryId) params = params.set('categoryId', filter.categoryId);
      if (filter.status) params = params.set('status', filter.status);
      if (filter.search) params = params.set('search', filter.search);
    }

    return this.http.get<FinancialTransaction[]>(this.apiUrl, { params }).pipe(
      tap({
        next: (data) => {
          this.transactions.set(data);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  getAccountTransactions(
    accountId: string,
    from?: string,
    to?: string
  ): Observable<FinancialTransaction[]> {
    // CAJ-11: days of operation (yyyy-MM-dd), both inclusive
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);

    return this.http.get<FinancialTransaction[]>(
      `/api/finances/accounts/${accountId}/transactions`,
      { params }
    );
  }

  getTransactionById(id: string): Observable<FinancialTransaction> {
    return this.http.get<FinancialTransaction>(`${this.apiUrl}/${id}`);
  }

  getExpensesByCashSession(cashSessionId: string): Observable<FinancialTransaction[]> {
    return this.http.get<FinancialTransaction[]>(`${this.apiUrl}/session/${cashSessionId}`);
  }

  createTransaction(request: FinancialTransactionRequest): Observable<FinancialTransaction> {
    this.isLoading.set(true);
    return this.http.post<FinancialTransaction>(this.apiUrl, request).pipe(
      tap({
        next: (newTx) => {
          this.transactions.update((current) => [newTx, ...current]);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  createCashExpense(request: CashExpenseRequest): Observable<FinancialTransaction> {
    this.isLoading.set(true);
    return this.http.post<FinancialTransaction>(`${this.apiUrl}/cash-expense`, request).pipe(
      tap({
        next: (newTx) => {
          this.transactions.update((current) => [newTx, ...current]);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  updateTransaction(
    id: string,
    request: FinancialTransactionRequest
  ): Observable<FinancialTransaction> {
    this.isLoading.set(true);
    return this.http.put<FinancialTransaction>(`${this.apiUrl}/${id}`, request).pipe(
      tap({
        next: (updated) => {
          this.transactions.update((current) =>
            current.map((t) => (t.id === id ? updated : t))
          );
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  /** Voids an expense of the open session from the POS (POS screen permission). */
  voidCashExpense(id: string, request?: VoidFinancialTransactionRequest): Observable<FinancialTransaction> {
    return this.http.post<FinancialTransaction>(`${this.apiUrl}/cash-expense/${id}/void`, request || {});
  }

  /** Accounts other than Caja for cash ins and outs from the POS (CAJ-08). */
  getCashTransferAccounts(): Observable<CashTransferAccount[]> {
    return this.http.get<CashTransferAccount[]>(`${this.apiUrl}/cash-transfer/accounts`);
  }

  /** Registers a cash in or out as a transfer between Caja and another account (CAJ-08). */
  createCashTransfer(request: CashTransferRequest): Observable<FinancialTransaction> {
    return this.http.post<FinancialTransaction>(`${this.apiUrl}/cash-transfer`, request);
  }

  /** Voids a cash in or out of the open session from the POS. */
  voidCashTransfer(id: string, request: VoidFinancialTransactionRequest): Observable<FinancialTransaction> {
    return this.http.post<FinancialTransaction>(`${this.apiUrl}/cash-transfer/${id}/void`, request);
  }

  voidTransaction(
    id: string,
    request?: VoidFinancialTransactionRequest
  ): Observable<FinancialTransaction> {
    this.isLoading.set(true);
    return this.http.post<FinancialTransaction>(`${this.apiUrl}/${id}/void`, request || {}).pipe(
      tap({
        next: (updated) => {
          this.transactions.update((current) =>
            current.map((t) => (t.id === id ? updated : t))
          );
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }
}
