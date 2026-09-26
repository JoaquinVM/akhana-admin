import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Supplier, SupplierRequest } from './models/supplier.models';

@Injectable({
  providedIn: 'root'
})
export class SupplierService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/suppliers';

  getSuppliers(search?: string, status?: string): Observable<Supplier[]> {
    let params = new HttpParams();
    if (search && search.trim().length > 0) {
      params = params.set('search', search.trim());
    }
    if (status && status.trim().length > 0 && status !== 'ALL' && status !== 'ACTIVE_INACTIVE') {
      params = params.set('status', status.trim().toUpperCase());
    }
    return this.http.get<Supplier[]>(this.apiUrl, { params });
  }

  getSupplierById(id: string): Observable<Supplier> {
    return this.http.get<Supplier>(`${this.apiUrl}/${id}`);
  }

  createSupplier(request: SupplierRequest): Observable<Supplier> {
    return this.http.post<Supplier>(this.apiUrl, request);
  }

  updateSupplier(id: string, request: SupplierRequest): Observable<Supplier> {
    return this.http.put<Supplier>(`${this.apiUrl}/${id}`, request);
  }

  deleteSupplier(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
