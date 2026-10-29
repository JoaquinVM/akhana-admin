import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  CashSessionSummary,
  CashSessionDetail,
  CashOpeningProposal,
  OpenCashRequest,
  CloseCashRequest
} from './models/cash.models';

@Injectable({
  providedIn: 'root'
})
export class CashService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/cash-sessions';

  // Shared state of the open cash session; null while the cash is closed
  currentSession = signal<CashSessionSummary | null>(null);
  isLoadingCurrent = signal<boolean>(false);

  // The server answers 204 (null body) when there is no open session
  getCurrentSession(): Observable<CashSessionSummary | null> {
    this.isLoadingCurrent.set(true);
    return this.http.get<CashSessionSummary | null>(`${this.apiUrl}/current`).pipe(
      tap({
        next: (session) => {
          this.currentSession.set(session ?? null);
          this.isLoadingCurrent.set(false);
        },
        error: () => {
          this.currentSession.set(null);
          this.isLoadingCurrent.set(false);
        }
      })
    );
  }

  getOpeningProposal(): Observable<CashOpeningProposal> {
    return this.http.get<CashOpeningProposal>(`${this.apiUrl}/opening-proposal`);
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

  getAllSessions(): Observable<CashSessionSummary[]> {
    return this.http.get<CashSessionSummary[]>(this.apiUrl);
  }

  getSessionDetail(id: string): Observable<CashSessionDetail> {
    return this.http.get<CashSessionDetail>(`${this.apiUrl}/${id}`);
  }
}
