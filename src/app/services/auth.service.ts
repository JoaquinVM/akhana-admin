import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { User, UserRole } from '../models/domain.model';
import { StorageService } from './storage.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUserSubject: BehaviorSubject<User>;

  constructor(private storageService: StorageService) {
    const users = this.storageService.getUsers();
    // Default to Administrador to ensure full access to all features (catalog, products, sales, POS)
    const savedUserId = localStorage.getItem('akhana_active_user_id');
    const defaultUser = (savedUserId ? users.find(u => u.id === savedUserId) : null)
      || users.find(u => u.role === 'ADMIN')
      || users[0];

    this.currentUserSubject = new BehaviorSubject<User>(defaultUser);
  }

  get currentUser$(): Observable<User> {
    return this.currentUserSubject.asObservable();
  }

  get currentUser(): User {
    return this.currentUserSubject.value;
  }

  isAdmin(): boolean {
    return this.currentUserSubject.value.role === 'ADMIN';
  }

  isSeller(): boolean {
    return this.currentUserSubject.value.role === 'VENDEDOR';
  }

  switchRole(role: UserRole): void {
    const users = this.storageService.getUsers();
    const user = users.find(u => u.role === role);
    if (user) {
      this.currentUserSubject.next(user);
      localStorage.setItem('akhana_active_user_id', user.id);
    }
  }

  setUser(user: User): void {
    this.currentUserSubject.next(user);
    localStorage.setItem('akhana_active_user_id', user.id);
  }
}
