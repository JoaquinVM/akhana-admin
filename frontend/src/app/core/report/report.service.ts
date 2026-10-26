import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';
import {
  ConsumptionsReport, ReportFile, ReportName, ReportRange, SalesByDayReport, SalesBySupplierReport, TotalsReport, TransactionsByCategoryReport
} from './models/report.models';

@Injectable({
  providedIn: 'root'
})
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/reports';

  // Sin fechas el servidor devuelve el mes en curso hasta hoy
  getSalesByDay(range: ReportRange = {}): Observable<SalesByDayReport> {
    return this.http.get<SalesByDayReport>(`${this.apiUrl}/sales-by-day`, { params: this.rangeParams(range) });
  }

  getSalesBySupplier(range: ReportRange = {}): Observable<SalesBySupplierReport> {
    return this.http.get<SalesBySupplierReport>(`${this.apiUrl}/sales-by-supplier`, { params: this.rangeParams(range) });
  }

  getTransactionsByCategory(range: ReportRange = {}): Observable<TransactionsByCategoryReport> {
    return this.http.get<TransactionsByCategoryReport>(`${this.apiUrl}/transactions-by-category`,
      { params: this.rangeParams(range) });
  }

  getTotals(range: ReportRange = {}): Observable<TotalsReport> {
    return this.http.get<TotalsReport>(`${this.apiUrl}/totals`, { params: this.rangeParams(range) });
  }

  getConsumptions(range: ReportRange = {}): Observable<ConsumptionsReport> {
    return this.http.get<ConsumptionsReport>(`${this.apiUrl}/consumptions`, { params: this.rangeParams(range) });
  }

  // FIN-10: el servidor arma el .xlsx con los mismos números de la pantalla
  exportReport(report: ReportName, range: ReportRange = {}): Observable<ReportFile> {
    return this.http.get(`${this.apiUrl}/${report}/export`,
      { params: this.rangeParams(range), responseType: 'blob', observe: 'response' }).pipe(
      map(response => ({
        fileName: this.fileName(response.headers.get('Content-Disposition'))
          ?? `akhana-${report}-${range.from ?? ''}-${range.to ?? ''}.xlsx`,
        content: response.body ?? new Blob()
      })),
      // Con responseType blob el error JSON del servidor también llega como Blob: se lee para mostrar su mensaje
      catchError((err: HttpErrorResponse) => from(this.readError(err)).pipe(switchMap(error => throwError(() => error))))
    );
  }

  private fileName(contentDisposition: string | null): string | null {
    return contentDisposition?.match(/filename="?([^";]+)"?/)?.[1] ?? null;
  }

  private async readError(err: HttpErrorResponse): Promise<unknown> {
    if (err?.error instanceof Blob) {
      try {
        return { ...err, error: JSON.parse(await err.error.text()) };
      } catch {
        return { ...err, error: null };
      }
    }
    return err;
  }

  private rangeParams(range: ReportRange): HttpParams {
    let params = new HttpParams();
    if (range.from) {
      params = params.set('from', range.from);
    }
    if (range.to) {
      params = params.set('to', range.to);
    }
    return params;
  }
}
