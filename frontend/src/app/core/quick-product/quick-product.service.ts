import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import {
  QuickProductGroup,
  QuickProductGroupRequest,
  ReorderGroupsRequest,
  ReorderItemsRequest
} from './models/quick-product.models';

@Injectable({
  providedIn: 'root'
})
export class QuickProductService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/quick-products/groups';

  groups = signal<QuickProductGroup[]>([]);
  activeGroupId = signal<string | null>(null);
  isLoading = signal<boolean>(false);

  activeGroup = computed(() => {
    const currentId = this.activeGroupId();
    const allGroups = this.groups();
    if (!currentId && allGroups.length > 0) {
      return allGroups[0];
    }
    return allGroups.find((g) => g.id === currentId) ?? (allGroups[0] ?? null);
  });

  loadGroups(): Observable<QuickProductGroup[]> {
    this.isLoading.set(true);
    return this.http.get<QuickProductGroup[]>(this.apiUrl).pipe(
      tap({
        next: (data) => {
          this.groups.set(data);
          if (data.length > 0 && !this.activeGroupId()) {
            this.activeGroupId.set(data[0].id);
          }
          this.isLoading.set(false);
        },
        error: () => {
          this.groups.set([]);
          this.isLoading.set(false);
        }
      })
    );
  }

  createGroup(request: QuickProductGroupRequest): Observable<QuickProductGroup> {
    return this.http.post<QuickProductGroup>(this.apiUrl, request).pipe(
      tap((newGroup) => {
        this.groups.update((current) => [...current, newGroup]);
        this.activeGroupId.set(newGroup.id);
      })
    );
  }

  updateGroup(id: string, request: QuickProductGroupRequest): Observable<QuickProductGroup> {
    return this.http.put<QuickProductGroup>(`${this.apiUrl}/${id}`, request).pipe(
      tap((updated) => {
        this.groups.update((current) =>
          current.map((g) => (g.id === id ? updated : g))
        );
      })
    );
  }

  deleteGroup(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap(() => {
        this.groups.update((current) => current.filter((g) => g.id !== id));
        const remaining = this.groups();
        this.activeGroupId.set(remaining.length > 0 ? remaining[0].id : null);
      })
    );
  }

  reorderGroups(groupIds: string[]): Observable<QuickProductGroup[]> {
    const payload: ReorderGroupsRequest = { groupIds };
    return this.http.put<QuickProductGroup[]>(`${this.apiUrl}/reorder`, payload).pipe(
      tap((updatedGroups) => {
        this.groups.set(updatedGroups);
      })
    );
  }

  reorderGroupItems(groupId: string, productIds: string[]): Observable<QuickProductGroup> {
    const payload: ReorderItemsRequest = { productIds };
    return this.http.put<QuickProductGroup>(`${this.apiUrl}/${groupId}/items/reorder`, payload).pipe(
      tap((updatedGroup) => {
        this.groups.update((current) =>
          current.map((g) => (g.id === groupId ? updatedGroup : g))
        );
      })
    );
  }
}
