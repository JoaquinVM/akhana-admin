import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Category, CategoryRequest } from './models/category.models';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/categories';

  getCategories(search?: string, status?: string): Observable<Category[]> {
    let params = new HttpParams();
    if (search && search.trim().length > 0) {
      params = params.set('search', search.trim());
    }
    if (status && status.trim().length > 0 && status !== 'ALL') {
      params = params.set('status', status.trim().toUpperCase());
    }
    return this.http.get<Category[]>(this.apiUrl, { params });
  }

  getCategoryById(id: string): Observable<Category> {
    return this.http.get<Category>(`${this.apiUrl}/${id}`);
  }

  createCategory(request: CategoryRequest): Observable<Category> {
    return this.http.post<Category>(this.apiUrl, request);
  }

  updateCategory(id: string, request: CategoryRequest): Observable<Category> {
    return this.http.put<Category>(`${this.apiUrl}/${id}`, request);
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
