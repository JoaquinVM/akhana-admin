import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Sale, SaleDetailRequest, VoidSaleRequest } from './models/sale.models';
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

  getAllSales(): Observable<Sale[]> {
    this.isLoading.set(true);
    return this.http.get<Sale[]>(this.apiUrl).pipe(
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
}
