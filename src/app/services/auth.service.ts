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
    // Default to Vendedor as per POS operation (Juan Pérez)
    const defaultUser = users.find(u => u.role === 'VENDEDOR') || users[0];
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
    }
  }

  setUser(user: User): void {
    this.currentUserSubject.next(user);
  }
}
