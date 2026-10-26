import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Consumption,
  ConsumptionFilters,
  ConsumptionRequest,
  VoidConsumptionRequest
} from './models/consumption.models';

@Injectable({
  providedIn: 'root'
})
export class ConsumptionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/consumptions';

  getConsumptions(filters: ConsumptionFilters = {}): Observable<Consumption[]> {
    let params = new HttpParams();
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    if (filters.productId) {
      params = params.set('productId', filters.productId);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.search && filters.search.trim().length > 0) {
      params = params.set('search', filters.search.trim());
    }
    return this.http.get<Consumption[]>(this.apiUrl, { params });
  }

  getConsumptionById(id: string): Observable<Consumption> {
    return this.http.get<Consumption>(`${this.apiUrl}/${id}`);
  }

  registerConsumption(request: ConsumptionRequest): Observable<Consumption> {
    return this.http.post<Consumption>(this.apiUrl, request);
  }

  voidConsumption(id: string, request: VoidConsumptionRequest): Observable<Consumption> {
    return this.http.post<Consumption>(`${this.apiUrl}/${id}/void`, request);
  }
}
