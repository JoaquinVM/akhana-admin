import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { FinancialSummary } from './models/finance.models';

@Injectable({
  providedIn: 'root'
})
export class FinanceSummaryService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/finances/summary';

  summary = signal<FinancialSummary | null>(null);
  isLoading = signal<boolean>(false);

  getSummary(): Observable<FinancialSummary> {
    this.isLoading.set(true);
    return this.http.get<FinancialSummary>(this.apiUrl).pipe(
      tap({
        next: (data) => {
          this.summary.set(data);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }
}
