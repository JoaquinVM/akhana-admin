import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { LoginRequest, LoginResponse } from './models/auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('should be created and initially unauthenticated when storage is empty', () => {
    expect(service).toBeTruthy();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.getToken()).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
  });

  it('should authenticate user, store token and session, and update signals on login success', () => {
    const credentials: LoginRequest = { username: 'admin', password: 'password123' };
    const mockResponse: LoginResponse = {
      token: 'jwt.test.token',
      id: '123e4567-e89b-12d3-a456-426614174000',
      username: 'admin',
      fullName: 'Administrador General',
      role: 'ADMINISTRATOR',
      permissions: ['POS', 'USERS'],
      message: 'Authentication successful'
    };

    service.login(credentials).subscribe((response) => {
      expect(response).toEqual(mockResponse);
    });

    const req = httpTesting.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(credentials);
    req.flush(mockResponse);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getToken()).toBe('jwt.test.token');
    expect(service.userRole()).toBe('ADMINISTRATOR');
    expect(service.permissions()).toEqual(['POS', 'USERS']);
    expect(service.hasPermission('POS')).toBe(true);
    expect(service.hasPermission('FINANCES_SUMMARY')).toBe(false);
    expect(service.getCurrentUser()).toEqual({
      id: '123e4567-e89b-12d3-a456-426614174000',
      username: 'admin',
      fullName: 'Administrador General',
      role: 'ADMINISTRATOR',
      permissions: ['POS', 'USERS'],
      mustChangePassword: false
    });
    expect(localStorage.getItem('akhana_token')).toBe('jwt.test.token');
  });

  it('should discard a stored session with a role from before Operador/Administrador', () => {
    localStorage.setItem('akhana_token', 'old.token');
    localStorage.setItem('akhana_user', JSON.stringify({ id: '1', username: 'seller', role: 'SELLER', permissions: ['SALES_POS'] }));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const restoredService = TestBed.inject(AuthService);

    expect(restoredService.getCurrentUser()).toBeNull();
    expect(restoredService.isAuthenticated()).toBe(false);
  });

  it('should clear session, update signals to null, and navigate immediately to /login on logout', () => {
    localStorage.setItem('akhana_token', 'sample.token');
    localStorage.setItem('akhana_user', JSON.stringify({ id: '1', username: 'operator', role: 'OPERATOR' }));

    const navigateSpy = vi.spyOn(router, 'navigate');

    const restoredService = TestBed.inject(AuthService);
    restoredService.logout();

    expect(restoredService.isAuthenticated()).toBe(false);
    expect(restoredService.getToken()).toBeNull();
    expect(restoredService.getCurrentUser()).toBeNull();
    expect(localStorage.getItem('akhana_token')).toBeNull();
    expect(localStorage.getItem('akhana_user')).toBeNull();
    expect(navigateSpy).toHaveBeenCalledTimes(1);
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should flag a required password change on login and clear it after changing the password', () => {
    service.login({ username: 'admin', password: 'initial-pass' }).subscribe();
    httpTesting.expectOne('/api/auth/login').flush({
      token: 'jwt.first.login',
      id: 'uuid-admin',
      username: 'admin',
      role: 'ADMINISTRATOR',
      permissions: [],
      mustChangePassword: true,
      message: 'Password change required'
    } satisfies LoginResponse);

    expect(service.mustChangePassword()).toBe(true);

    service.changePassword({ currentPassword: 'initial-pass', newPassword: 'new-strong-pass' }).subscribe();
    const req = httpTesting.expectOne('/api/auth/change-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ currentPassword: 'initial-pass', newPassword: 'new-strong-pass' });
    req.flush(null);

    expect(service.mustChangePassword()).toBe(false);
    expect(service.getToken()).toBe('jwt.first.login');
    expect(JSON.parse(localStorage.getItem('akhana_user')!).mustChangePassword).toBe(false);
  });
});
