import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Tag, TagRequest } from './models/tag.models';

@Injectable({
  providedIn: 'root'
})
export class TagService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/tags';

  getTags(search?: string, status?: string): Observable<Tag[]> {
    let params = new HttpParams();
    if (search && search.trim().length > 0) {
      params = params.set('search', search.trim());
    }
    if (status && status.trim().length > 0 && status !== 'ALL') {
      params = params.set('status', status.trim().toUpperCase());
    }
    return this.http.get<Tag[]>(this.apiUrl, { params });
  }

  getTagById(id: string): Observable<Tag> {
    return this.http.get<Tag>(`${this.apiUrl}/${id}`);
  }

  createTag(request: TagRequest): Observable<Tag> {
    return this.http.post<Tag>(this.apiUrl, request);
  }

  updateTag(id: string, request: TagRequest): Observable<Tag> {
    return this.http.put<Tag>(`${this.apiUrl}/${id}`, request);
  }

  deleteTag(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
