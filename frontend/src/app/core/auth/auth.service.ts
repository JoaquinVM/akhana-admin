import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ChangePasswordRequest, LoginRequest, LoginResponse, UserSession } from './models/auth.models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly TOKEN_KEY = 'akhana_token';
  private readonly USER_KEY = 'akhana_user';
  private readonly LOGIN_URL = '/api/auth/login';
  private readonly ME_URL = '/api/auth/me';
  private readonly CHANGE_PASSWORD_URL = '/api/auth/change-password';

  // Reactive State via Angular Signals
  private readonly _currentUser = signal<UserSession | null>(this.restoreSession());
  readonly currentUser = this._currentUser.asReadonly();

  readonly isAuthenticated = computed(() => !!this._currentUser() && !!this.getToken());
  readonly userRole = computed(() => this._currentUser()?.role ?? null);
  readonly permissions = computed(() => this._currentUser()?.permissions ?? []);
  readonly mustChangePassword = computed(() => !!this._currentUser()?.mustChangePassword);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.LOGIN_URL, credentials).pipe(
      tap((response) => {
        if (response && response.token) {
          const userSession: UserSession = {
            id: response.id,
            username: response.username,
            fullName: response.fullName,
            role: response.role,
            permissions: response.permissions ? Array.from(response.permissions) : [],
            mustChangePassword: !!response.mustChangePassword
          };
          this.saveSession(response.token, userSession);
        }
      })
    );
  }

  fetchCurrentUserProfile(): Observable<any> {
    return this.http.get<any>(this.ME_URL).pipe(
      tap((profile) => {
        if (profile && this._currentUser()) {
          const updated: UserSession = {
            ...this._currentUser()!,
            fullName: profile.fullName || this._currentUser()!.fullName,
            role: profile.role || this._currentUser()!.role,
            permissions: profile.permissions ? Array.from(profile.permissions) : this._currentUser()!.permissions,
            mustChangePassword: profile.mustChangePassword ?? this._currentUser()!.mustChangePassword
          };
          const token = this.getToken() || '';
          this.saveSession(token, updated);
        }
      })
    );
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(this.CHANGE_PASSWORD_URL, request).pipe(
      tap(() => {
        const user = this._currentUser();
        if (user) {
          this.saveSession(this.getToken() || '', { ...user, mustChangePassword: false });
        }
      })
    );
  }

  hasPermission(permissionKey: string): boolean {
    return this.permissions().includes(permissionKey);
  }

  logout(): void {
    this.clearSession();
    this.router.navigate(['/login']);
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
