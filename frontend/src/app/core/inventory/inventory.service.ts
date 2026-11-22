import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Kardex, StockAdjustmentRequest, StockCount, StockCountLineRequest, StockCountReview, StockItem, StockLevel
} from './models/inventory.models';

// V2 · Inventario: Existencias, Kardex and Conteo (INV-01 to INV-05, INV-18)
@Injectable({
  providedIn: 'root'
})
export class InventoryService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/inventory';

  getStock(categoryId?: string | null): Observable<StockItem[]> {
    let params = new HttpParams();
    if (categoryId) params = params.set('categoryId', categoryId);
    return this.http.get<StockItem[]>(`${this.apiUrl}/stock`, { params });
  }

  getLevels(): Observable<StockLevel[]> {
    return this.http.get<StockLevel[]>(`${this.apiUrl}/levels`);
  }

  getKardex(productId: string, from?: string | null, to?: string | null): Observable<Kardex> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<Kardex>(`${this.apiUrl}/products/${productId}/kardex`, { params });
  }

  adjust(productId: string, request: StockAdjustmentRequest): Observable<Kardex> {
    return this.http.post<Kardex>(`${this.apiUrl}/products/${productId}/adjustments`, request);
  }

  getCounts(): Observable<StockCount[]> {
    return this.http.get<StockCount[]>(`${this.apiUrl}/counts`);
  }

  createCount(categoryId: string | null): Observable<StockCount> {
    return this.http.post<StockCount>(`${this.apiUrl}/counts`, { categoryId });
  }

  getCount(id: string): Observable<StockCount> {
    return this.http.get<StockCount>(`${this.apiUrl}/counts/${id}`);
  }

  saveCountLine(countId: string, productId: string, request: StockCountLineRequest): Observable<StockCount> {
    return this.http.put<StockCount>(`${this.apiUrl}/counts/${countId}/lines/${productId}`, request);
  }

  reviewCount(id: string): Observable<StockCountReview> {
    return this.http.get<StockCountReview>(`${this.apiUrl}/counts/${id}/review`);
  }

  confirmCount(id: string): Observable<StockCount> {
    return this.http.post<StockCount>(`${this.apiUrl}/counts/${id}/confirm`, {});
  }

  discardCount(id: string): Observable<StockCount> {
    return this.http.post<StockCount>(`${this.apiUrl}/counts/${id}/discard`, {});
  }
}
