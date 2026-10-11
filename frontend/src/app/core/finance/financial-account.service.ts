import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { FinancialAccount, FinancialAccountRequest } from './models/finance.models';

@Injectable({
  providedIn: 'root'
})
export class FinancialAccountService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/finances/accounts';

  accounts = signal<FinancialAccount[]>([]);
  isLoading = signal<boolean>(false);

  getAccounts(): Observable<FinancialAccount[]> {
    this.isLoading.set(true);
    return this.http.get<FinancialAccount[]>(this.apiUrl).pipe(
      tap({
        next: (data) => {
          this.accounts.set(data);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  getAccountById(id: string): Observable<FinancialAccount> {
    return this.http.get<FinancialAccount>(`${this.apiUrl}/${id}`);
  }

  createAccount(request: FinancialAccountRequest): Observable<FinancialAccount> {
    this.isLoading.set(true);
    return this.http.post<FinancialAccount>(this.apiUrl, request).pipe(
      tap({
        next: (newAccount) => {
          this.accounts.update((current) => [...current, newAccount]);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  updateAccount(id: string, request: FinancialAccountRequest): Observable<FinancialAccount> {
    this.isLoading.set(true);
    return this.http.put<FinancialAccount>(`${this.apiUrl}/${id}`, request).pipe(
      tap({
        next: (updated) => {
          this.accounts.update((current) =>
            current.map((a) => (a.id === id ? updated : a))
          );
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  setQrAccount(id: string): Observable<FinancialAccount> {
    this.isLoading.set(true);
    return this.http.put<FinancialAccount>(`${this.apiUrl}/${id}/qr`, {}).pipe(
      tap({
        next: (updated) => {
          this.accounts.update((current) =>
            current.map((a) => ({
              ...a,
              isQrAccount: a.id === id
            }))
          );
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  deleteAccount(id: string): Observable<void> {
    this.isLoading.set(true);
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap({
        next: () => {
          this.accounts.update((current) => current.filter((a) => a.id !== id));
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }
}
