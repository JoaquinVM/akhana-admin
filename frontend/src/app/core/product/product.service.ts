import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BarcodePendingProduct, Product, ProductRequest, ProductStatus } from './models/product.models';
import { ProductImportResult } from './models/product-import.models';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/products';

  getProducts(search?: string, status?: string): Observable<Product[]> {
    let params = new HttpParams();
    if (search && search.trim().length > 0) {
      params = params.set('search', search.trim());
    }
    if (status && status.trim().length > 0 && status !== 'ALL' && status !== 'ACTIVE_INACTIVE') {
      params = params.set('status', status.trim().toUpperCase());
    }
    return this.http.get<Product[]>(this.apiUrl, { params });
  }

  getProductById(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.apiUrl}/${id}`);
  }

  createProduct(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>(this.apiUrl, request);
  }

  updateProduct(id: string, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/${id}`, request);
  }

  changeProductStatus(id: string, status: ProductStatus): Observable<Product> {
    return this.http.patch<Product>(`${this.apiUrl}/${id}/status`, null, {
      params: new HttpParams().set('status', status)
    });
  }

  deleteProduct(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // POS-15: 404 when no product has this barcode
  getProductByBarcode(barcode: string): Observable<Product> {
    return this.http.get<Product>(`${this.apiUrl}/by-barcode/${encodeURIComponent(barcode)}`);
  }

  // CAT-10
  getBarcodePending(): Observable<BarcodePendingProduct[]> {
    return this.http.get<BarcodePendingProduct[]>(`${this.apiUrl}/barcode-pending`);
  }

  addBarcode(id: string, barcode: string): Observable<Product> {
    return this.http.post<Product>(`${this.apiUrl}/${id}/barcodes`, { barcode });
  }

  markNoBarcode(id: string): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/${id}/no-barcode`, null);
  }

  clearNoBarcode(id: string): Observable<Product> {
    return this.http.delete<Product>(`${this.apiUrl}/${id}/no-barcode`);
  }

  // CAT-06: Revisar validates without saving; Importar validates the same file again and saves
  previewImport(file: File): Observable<ProductImportResult> {
    return this.http.post<ProductImportResult>(`${this.apiUrl}/import/preview`, this.importForm(file));
  }

  importProducts(file: File): Observable<ProductImportResult> {
    return this.http.post<ProductImportResult>(`${this.apiUrl}/import`, this.importForm(file));
  }

  private importForm(file: File): FormData {
    const form = new FormData();
    form.append('file', file, file.name);
    return form;
  }
}
