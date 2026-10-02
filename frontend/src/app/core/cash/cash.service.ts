import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  CashSessionSummary,
  CashSessionDetail,
  OpenCashRequest,
  CloseCashRequest,
  SaleRequest,
  Sale
} from './models/cash.models';

@Injectable({
  providedIn: 'root'
})
export class CashService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/cash-sessions';

  // Estados Reactivos con Signals
  currentSession = signal<CashSessionSummary | null>(null);
  isLoadingCurrent = signal<boolean>(false);

  getCurrentSession(): Observable<CashSessionSummary> {
    this.isLoadingCurrent.set(true);
    return this.http.get<CashSessionSummary>(`${this.apiUrl}/current`).pipe(
      tap({
        next: (session) => {
          this.currentSession.set(session);
          this.isLoadingCurrent.set(false);
        },
        error: () => {
          this.currentSession.set(null);
          this.isLoadingCurrent.set(false);
        }
      })
    );
  }

  openSession(request: OpenCashRequest): Observable<CashSessionSummary> {
    return this.http.post<CashSessionSummary>(`${this.apiUrl}/open`, request).pipe(
      tap((session) => this.currentSession.set(session))
    );
  }

  closeSession(request: CloseCashRequest): Observable<CashSessionSummary> {
    return this.http.post<CashSessionSummary>(`${this.apiUrl}/current/close`, request).pipe(
      tap(() => this.currentSession.set(null))
    );
  }

  registerSale(request: SaleRequest): Observable<Sale> {
    return this.http.post<Sale>(`${this.apiUrl}/current/sales`, request).pipe(
      tap(() => {
        // Refrescar métricas de la sesión tras la venta
        this.getCurrentSession().subscribe();
      })
    );
  }

  getAllSessions(): Observable<CashSessionSummary[]> {
    return this.http.get<CashSessionSummary[]>(this.apiUrl);
  }

  getSessionDetail(id: string): Observable<CashSessionDetail> {
    return this.http.get<CashSessionDetail>(`${this.apiUrl}/${id}`);
  }
}
