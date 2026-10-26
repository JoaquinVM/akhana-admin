import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Customer,
  CustomerCollectionRequest,
  CustomerHistory,
  CustomerRequest,
  CustomerStatus,
  VoidDebtRequest
} from './models/debt.models';

@Injectable({
  providedIn: 'root'
})
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/customers';

  // Sin estado: activos e inactivos (sin eliminados). El POS lee los activos para la venta a crédito
  getCustomers(status: CustomerStatus | null = null, search?: string): Observable<Customer[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    if (search && search.trim().length > 0) {
      params = params.set('search', search.trim());
    }
    return this.http.get<Customer[]>(this.apiUrl, { params });
  }

  createCustomer(request: CustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(this.apiUrl, request);
  }

  updateCustomer(id: string, request: CustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`${this.apiUrl}/${id}`, request);
  }

  deleteCustomer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getHistory(id: string): Observable<CustomerHistory> {
    return this.http.get<CustomerHistory>(`${this.apiUrl}/${id}/history`);
  }

  registerCollection(id: string, request: CustomerCollectionRequest): Observable<CustomerHistory> {
    return this.http.post<CustomerHistory>(`${this.apiUrl}/${id}/collections`, request);
  }

  voidCollection(id: string, collectionId: string, request: VoidDebtRequest): Observable<CustomerHistory> {
    return this.http.post<CustomerHistory>(`${this.apiUrl}/${id}/collections/${collectionId}/void`, request);
  }
}
