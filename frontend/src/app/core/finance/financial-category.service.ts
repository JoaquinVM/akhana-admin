import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  FinancialCategory,
  FinancialCategoryRequest,
  FinancialCategoryType
} from './models/finance.models';

@Injectable({
  providedIn: 'root'
})
export class FinancialCategoryService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/finances/categories';

  categories = signal<FinancialCategory[]>([]);
  isLoading = signal<boolean>(false);

  getCategories(type?: FinancialCategoryType): Observable<FinancialCategory[]> {
    this.isLoading.set(true);
    let params = new HttpParams();
    if (type) {
      params = params.set('type', type);
    }

    return this.http.get<FinancialCategory[]>(this.apiUrl, { params }).pipe(
      tap({
        next: (data) => {
          this.categories.set(data);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  getCategoryById(id: string): Observable<FinancialCategory> {
    return this.http.get<FinancialCategory>(`${this.apiUrl}/${id}`);
  }

  createCategory(request: FinancialCategoryRequest): Observable<FinancialCategory> {
    this.isLoading.set(true);
    return this.http.post<FinancialCategory>(this.apiUrl, request).pipe(
      tap({
        next: (newCategory) => {
          this.categories.update((current) => [...current, newCategory]);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  updateCategory(id: string, request: FinancialCategoryRequest): Observable<FinancialCategory> {
    this.isLoading.set(true);
    return this.http.put<FinancialCategory>(`${this.apiUrl}/${id}`, request).pipe(
      tap({
        next: (updated) => {
          this.categories.update((current) =>
            current.map((c) => (c.id === id ? updated : c))
          );
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  setSalesDefaultCategory(id: string): Observable<FinancialCategory> {
    this.isLoading.set(true);
    return this.http.put<FinancialCategory>(`${this.apiUrl}/${id}/sales-default`, {}).pipe(
      tap({
        next: (updated) => {
          this.categories.update((current) =>
            current.map((c) => ({
              ...c,
              isSalesDefault: c.id === id
            }))
          );
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }

  deleteCategory(id: string): Observable<void> {
    this.isLoading.set(true);
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap({
        next: () => {
          this.categories.update((current) => current.filter((c) => c.id !== id));
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      })
    );
  }
}
