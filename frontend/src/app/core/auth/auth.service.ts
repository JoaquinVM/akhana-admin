import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, UserSession } from './models/auth.models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly TOKEN_KEY = 'akhana_token';
  private readonly USER_KEY = 'akhana_user';
  private readonly LOGIN_URL = '/api/auth/login';

  // Reactive State via Angular Signals
  private readonly _currentUser = signal<UserSession | null>(this.restoreSession());
  readonly currentUser = this._currentUser.asReadonly();

  readonly isAuthenticated = computed(() => !!this._currentUser() && !!this.getToken());
  readonly userRole = computed(() => this._currentUser()?.role ?? null);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.LOGIN_URL, credentials).pipe(
      tap((response) => {
        if (response && response.token) {
          const userSession: UserSession = {
            id: response.id,
            username: response.username,
            role: response.role
          };
          this.saveSession(response.token, userSession);
        }
      })
    );
  }

  logout(): void {
    this.clearSession();
  }

  getToken(): string | null {
    try {
      return localStorage.getItem(this.TOKEN_KEY);
    } catch {
      return null;
    }
  }

  getCurrentUser(): UserSession | null {
    return this._currentUser();
  }

  clearSession(): void {
    try {
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USER_KEY);
    } catch {
      // Ignored for non-browser/restricted storage environments
    }
    this._currentUser.set(null);
  }

  private saveSession(token: string, user: UserSession): void {
    try {
      localStorage.setItem(this.TOKEN_KEY, token);
      localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    } catch {
      // Storage fallback
    }
    this._currentUser.set(user);
  }

  private restoreSession(): UserSession | null {
    try {
      const token = localStorage.getItem(this.TOKEN_KEY);
      const userRaw = localStorage.getItem(this.USER_KEY);
      if (token && userRaw) {
        return JSON.parse(userRaw) as UserSession;
      }
    } catch {
      return null;
    }
    return null;
  }
}
