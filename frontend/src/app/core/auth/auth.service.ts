import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ChangePasswordRequest, LoginRequest, LoginResponse, Role, UserSession } from './models/auth.models';

const KNOWN_ROLES: Role[] = ['OPERATOR', 'ADMINISTRATOR'];

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

  // Set when the server rejects the token of an open session (expired, or the password changed elsewhere), so
  // the login screen can say why it is back there
  private sessionEnded = false;
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

  // The server invalidates the tokens issued before the change and returns a new one to stay in this session
  changePassword(request: ChangePasswordRequest): Observable<{ token: string }> {
    return this.http.post<{ token: string }>(this.CHANGE_PASSWORD_URL, request).pipe(
      tap((response) => {
        const user = this._currentUser();
        if (user) {
          this.saveSession(response?.token || this.getToken() || '', { ...user, mustChangePassword: false });
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

  markSessionEnded(): void {
    this.sessionEnded = true;
  }

  // Read once: the notice shows on the next login screen only
  consumeSessionEnded(): boolean {
    const ended = this.sessionEnded;
    this.sessionEnded = false;
    return ended;
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
        const user = JSON.parse(userRaw) as UserSession;
        // Sessions saved before the Operador/Administrador roles carry old keys: sign in again
        return KNOWN_ROLES.includes(user.role) ? user : null;
      }
    } catch {
      return null;
    }
    return null;
  }
}
