import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { LoginRequest, LoginResponse } from './models/auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
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
      role: 'ADMIN',
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
    expect(service.userRole()).toBe('ADMIN');
    expect(service.getCurrentUser()).toEqual({
      id: '123e4567-e89b-12d3-a456-426614174000',
      username: 'admin',
      role: 'ADMIN'
    });
    expect(localStorage.getItem('akhana_token')).toBe('jwt.test.token');
  });

  it('should clear session and update signals to null on logout', () => {
    localStorage.setItem('akhana_token', 'sample.token');
    localStorage.setItem('akhana_user', JSON.stringify({ id: '1', username: 'seller', role: 'SELLER' }));

    const restoredService = TestBed.inject(AuthService);
    restoredService.logout();

    expect(restoredService.isAuthenticated()).toBe(false);
    expect(restoredService.getToken()).toBeNull();
    expect(restoredService.getCurrentUser()).toBeNull();
    expect(localStorage.getItem('akhana_token')).toBeNull();
    expect(localStorage.getItem('akhana_user')).toBeNull();
  });
});
