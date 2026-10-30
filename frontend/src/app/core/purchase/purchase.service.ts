import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Payables,
  PaymentAccount,
  PriceSuggestion,
  Purchase,
  PurchaseDeliveryRequest,
  PurchaseFilters,
  PurchasePaymentRequest,
  PurchaseRequest,
  PurchaseUpdateRequest,
  VoidPurchaseRequest
} from './models/purchase.models';

@Injectable({
  providedIn: 'root'
})
export class PurchaseService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/purchases';

  getPurchases(filters: PurchaseFilters = {}): Observable<Purchase[]> {
    let params = new HttpParams();
    if (filters.supplierId) {
      params = params.set('supplierId', filters.supplierId);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.paymentStatus) {
      params = params.set('paymentStatus', filters.paymentStatus);
    }
    if (filters.deliveryStatus) {
      params = params.set('deliveryStatus', filters.deliveryStatus);
    }
    if (filters.search && filters.search.trim().length > 0) {
      params = params.set('search', filters.search.trim());
    }
    return this.http.get<Purchase[]>(this.apiUrl, { params });
  }

  getPurchaseById(id: string): Observable<Purchase> {
    return this.http.get<Purchase>(`${this.apiUrl}/${id}`);
  }

  registerPurchase(request: PurchaseRequest): Observable<Purchase> {
    return this.http.post<Purchase>(this.apiUrl, request);
  }

  updatePurchase(purchaseId: string, request: PurchaseUpdateRequest): Observable<Purchase> {
    return this.http.put<Purchase>(`${this.apiUrl}/${purchaseId}`, request);
  }

  registerPayment(purchaseId: string, request: PurchasePaymentRequest): Observable<Purchase> {
    return this.http.post<Purchase>(`${this.apiUrl}/${purchaseId}/payments`, request);
  }

  getPayables(supplierId: string | null = null): Observable<Payables> {
    let params = new HttpParams();
    if (supplierId) {
      params = params.set('supplierId', supplierId);
    }
    return this.http.get<Payables>(`${this.apiUrl}/payables`, { params });
  }

  updateDelivery(purchaseId: string, request: PurchaseDeliveryRequest): Observable<Purchase> {
    return this.http.put<Purchase>(`${this.apiUrl}/${purchaseId}/delivery`, request);
  }

  voidPurchase(purchaseId: string, request: VoidPurchaseRequest): Observable<Purchase> {
    return this.http.post<Purchase>(`${this.apiUrl}/${purchaseId}/void`, request);
  }

  voidPayment(purchaseId: string, paymentId: string, request: VoidPurchaseRequest): Observable<Purchase> {
    return this.http.post<Purchase>(`${this.apiUrl}/${purchaseId}/payments/${paymentId}/void`, request);
  }

  getPaymentAccounts(): Observable<PaymentAccount[]> {
    return this.http.get<PaymentAccount[]>(`${this.apiUrl}/payment-accounts`);
  }

  getPriceSuggestion(productId: string, newCost: number): Observable<PriceSuggestion> {
    const params = new HttpParams().set('productId', productId).set('newCost', String(newCost));
    return this.http.get<PriceSuggestion>(`${this.apiUrl}/price-suggestion`, { params });
  }
}
