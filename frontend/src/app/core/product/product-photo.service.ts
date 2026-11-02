import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

// CAT-12: the images go through HttpClient because <img src> cannot send the session token
@Injectable({
  providedIn: 'root'
})
export class ProductPhotoService {
  private readonly http = inject(HttpClient);

  // expectedPhotoId is the photo on screen; the server answers a conflict if it is no longer the current one
  upload(productId: string, file: File, expectedPhotoId: string | null): Observable<{ photoId: string }> {
    const form = new FormData();
    form.append('file', file, file.name);
    if (expectedPhotoId) {
      form.append('expectedPhotoId', expectedPhotoId);
    }
    return this.http.post<{ photoId: string }>(this.url(productId), form);
  }

  remove(productId: string, expectedPhotoId: string): Observable<void> {
    return this.http.delete<void>(this.url(productId), {
      params: new HttpParams().set('expectedPhotoId', expectedPhotoId)
    });
  }

  getImage(productId: string, photoId: string): Observable<Blob> {
    return this.http.get(`${this.url(productId)}/${photoId}/image`, { responseType: 'blob' });
  }

  getThumbnail(productId: string, photoId: string): Observable<Blob> {
    return this.http.get(`${this.url(productId)}/${photoId}/thumbnail`, { responseType: 'blob' });
  }

  private url(productId: string): string {
    return `/api/products/${productId}/photo`;
  }
}
