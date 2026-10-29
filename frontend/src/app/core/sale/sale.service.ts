import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { PageResponse, Sale, SaleDetailRequest, SaleFilters, VoidSaleRequest } from './models/sale.models';
import { CashService } from '../cash/cash.service';

@Injectable({
  providedIn: 'root'
})
export class SaleService {
  private readonly http = inject(HttpClient);
  private readonly cashService = inject(CashService);
  private readonly apiUrl = '/api/sales';

  sales = signal<Sale[]>([]);
  isLoading = signal<boolean>(false);

  registerSale(request: SaleDetailRequest): Observable<Sale> {
    this.isLoading.set(true);
    return this.http.post<Sale>(this.apiUrl, request).pipe(
      tap({
        next: (newSale) => {
          this.sales.update((current) => [newSale, ...current]);
          this.isLoading.set(false);
          this.cashService.getCurrentSession().subscribe();
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  voidSale(saleId: string, request: VoidSaleRequest): Observable<Sale> {
    this.isLoading.set(true);
    return this.http.post<Sale>(`${this.apiUrl}/${saleId}/void`, request).pipe(
      tap({
        next: (updatedSale) => {
          this.sales.update((current) =>
            current.map((s) => (s.id === saleId ? updatedSale : s))
          );
          this.isLoading.set(false);
          this.cashService.getCurrentSession().subscribe();
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  getSalesBySession(sessionId: string): Observable<Sale[]> {
    this.isLoading.set(true);
    return this.http.get<Sale[]>(`${this.apiUrl}/session/${sessionId}`).pipe(
      tap({
        next: (data) => {
          this.sales.set(data);
          this.isLoading.set(false);
        },
        error: () => {
          this.sales.set([]);
          this.isLoading.set(false);
        }
      })
    );
  }

  // POS-09: paginated history, newest first
  searchSales(filters: SaleFilters = {}, page = 0, size = 20): Observable<PageResponse<Sale>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const key of ['from', 'to', 'status', 'saleType', 'paymentMethod', 'createdBy'] as const) {
      const value = filters[key];
      if (value) {
        params = params.set(key, value);
      }
    }
    if (filters.pendingAssignment) {
      params = params.set('pendingAssignment', true);
    }
    return this.http.get<PageResponse<Sale>>(this.apiUrl, { params });
  }

  // Users who registered at least one sale, for the history filter
  getSellers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/sellers`);
  }

  getSaleById(saleId: string): Observable<Sale> {
    return this.http.get<Sale>(`${this.apiUrl}/${saleId}`);
  }

  // POS-12: the unidentified line takes the real product and its cost, once
  assignItem(saleId: string, itemId: string, productId: string): Observable<Sale> {
    return this.http.post<Sale>(`${this.apiUrl}/${saleId}/items/${itemId}/assign`, { productId });
  }

  closeUnidentified(saleId: string, itemId: string): Observable<Sale> {
    return this.http.post<Sale>(`${this.apiUrl}/${saleId}/items/${itemId}/close-unidentified`, {});
  }
}
