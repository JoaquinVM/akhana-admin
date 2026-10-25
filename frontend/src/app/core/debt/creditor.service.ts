import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Creditor,
  CreditorHistory,
  CreditorRepaymentRequest,
  CreditorRequest,
  CreditorStatus,
  DebtAccount,
  VoidDebtRequest
} from './models/debt.models';

@Injectable({
  providedIn: 'root'
})
export class CreditorService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/creditors';

  // Sin estado: activas e inactivas (sin eliminadas)
  getCreditors(status: CreditorStatus | null = null, search?: string): Observable<Creditor[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    if (search && search.trim().length > 0) {
      params = params.set('search', search.trim());
    }
    return this.http.get<Creditor[]>(this.apiUrl, { params });
  }

  createCreditor(request: CreditorRequest): Observable<Creditor> {
    return this.http.post<Creditor>(this.apiUrl, request);
  }

  updateCreditor(id: string, request: CreditorRequest): Observable<Creditor> {
    return this.http.put<Creditor>(`${this.apiUrl}/${id}`, request);
  }

  deleteCreditor(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getHistory(id: string): Observable<CreditorHistory> {
    return this.http.get<CreditorHistory>(`${this.apiUrl}/${id}/history`);
  }

  registerRepayment(id: string, request: CreditorRepaymentRequest): Observable<CreditorHistory> {
    return this.http.post<CreditorHistory>(`${this.apiUrl}/${id}/repayments`, request);
  }

  voidRepayment(id: string, repaymentId: string, request: VoidDebtRequest): Observable<CreditorHistory> {
    return this.http.post<CreditorHistory>(`${this.apiUrl}/${id}/repayments/${repaymentId}/void`, request);
  }

  // Cuentas para devolver, sin pasar por Finanzas
  getAccounts(): Observable<DebtAccount[]> {
    return this.http.get<DebtAccount[]>('/api/debts/accounts');
  }
}
