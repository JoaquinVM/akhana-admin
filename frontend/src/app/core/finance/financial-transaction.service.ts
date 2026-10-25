import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  CashExpenseRequest,
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
      if (filter.startDate) params = params.set('startDate', filter.startDate);
      if (filter.endDate) params = params.set('endDate', filter.endDate);
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
    startDate?: string,
    endDate?: string
  ): Observable<FinancialTransaction[]> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

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

  /** Anula un gasto de la caja abierta desde el POS (permiso de pantalla POS). */
  voidCashExpense(id: string, request?: VoidFinancialTransactionRequest): Observable<FinancialTransaction> {
    return this.http.post<FinancialTransaction>(`${this.apiUrl}/cash-expense/${id}/void`, request || {});
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
