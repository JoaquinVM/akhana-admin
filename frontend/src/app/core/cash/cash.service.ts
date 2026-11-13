import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  CashSessionSummary,
  CashSessionDetail,
  CashOpeningProposal,
  OpenCashRequest,
  CloseCashRequest
} from './models/cash.models';
import { CloseCashDraftService } from './close-cash-draft.service';

// POS-17: no answer, or a server failure, says nothing about the cash; a 204 or a 4xx is the server's answer
export function isConnectionFailure(error: HttpErrorResponse): boolean {
  return error.status === 0 || error.status >= 500;
}

@Injectable({
  providedIn: 'root'
})
export class CashService {
  private readonly http = inject(HttpClient);
  private readonly closeDraft = inject(CloseCashDraftService);
  private readonly apiUrl = '/api/cash-sessions';

  // Shared state of the open cash session; null while the cash is closed
  currentSession = signal<CashSessionSummary | null>(null);
  isLoadingCurrent = signal<boolean>(false);
  // POS-17: the last check got no answer, so currentSession is the last one known
  sessionCheckFailed = signal<boolean>(false);

  // The server answers 204 (null body) when there is no open session
  getCurrentSession(): Observable<CashSessionSummary | null> {
    this.isLoadingCurrent.set(true);
    return this.http.get<CashSessionSummary | null>(`${this.apiUrl}/current`).pipe(
      tap({
        next: (session) => {
          this.currentSession.set(session ?? null);
          this.sessionCheckFailed.set(false);
          this.isLoadingCurrent.set(false);
        },
        error: (error: HttpErrorResponse) => {
          const connectionFailure = isConnectionFailure(error);
          if (!connectionFailure) {
            this.currentSession.set(null);
          }
          this.sessionCheckFailed.set(connectionFailure);
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
      tap((session) => this.setKnownSession(session))
    );
  }

  closeSession(request: CloseCashRequest): Observable<CashSessionSummary> {
    return this.http.post<CashSessionSummary>(`${this.apiUrl}/current/close`, request).pipe(
      // CAJ-15: the count written in this browser goes away only when the cash is closed
      tap(() => {
        this.closeDraft.clear();
        this.setKnownSession(null);
      })
    );
  }

  // CAJ-14: the session is open again, so the POS and the menu badge change at once
  reopenSession(id: string, reason: string): Observable<CashSessionSummary> {
    return this.http.post<CashSessionSummary>(`${this.apiUrl}/${id}/reopen`, { reason }).pipe(
      tap((session) => this.setKnownSession(session))
    );
  }

  getAllSessions(): Observable<CashSessionSummary[]> {
    return this.http.get<CashSessionSummary[]>(this.apiUrl);
  }

  getSessionDetail(id: string): Observable<CashSessionDetail> {
    return this.http.get<CashSessionDetail>(`${this.apiUrl}/${id}`);
  }

  private setKnownSession(session: CashSessionSummary | null): void {
    this.currentSession.set(session);
    this.sessionCheckFailed.set(false);
  }
}
