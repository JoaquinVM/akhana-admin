import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ReportRange, SalesByDayReport, SalesBySupplierReport, TransactionsByCategoryReport
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
