import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PurchaseDraft, PurchaseReadingStatus } from './models/purchase.models';

// COM-20, COM-23: reading a text or a photo, and the invoice photo of a purchase. The images go through HttpClient
// because <img src> cannot send the session token
@Injectable({
  providedIn: 'root'
})
export class PurchaseReadingService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/purchases';

  getStatus(): Observable<PurchaseReadingStatus> {
    return this.http.get<PurchaseReadingStatus>(`${this.apiUrl}/readings/status`);
  }

  readText(text: string): Observable<PurchaseDraft> {
    return this.http.post<PurchaseDraft>(`${this.apiUrl}/readings/text`, { text });
  }

  readPhoto(file: File): Observable<PurchaseDraft> {
    return this.http.post<PurchaseDraft>(`${this.apiUrl}/readings/photo`, this.formWith(file));
  }

  uploadPhoto(purchaseId: string, file: File): Observable<{ photoId: string }> {
    return this.http.post<{ photoId: string }>(`${this.apiUrl}/${purchaseId}/photo`, this.formWith(file));
  }

  getPhotoImage(purchaseId: string, photoId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${purchaseId}/photo/${photoId}/image`, { responseType: 'blob' });
  }

  private formWith(file: File): FormData {
    const form = new FormData();
    form.append('file', file, file.name);
    return form;
  }
}
