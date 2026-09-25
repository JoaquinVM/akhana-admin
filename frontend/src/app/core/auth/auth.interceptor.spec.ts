import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;
  let authServiceSpy: { getToken: ReturnType<typeof vi.fn>; clearSession: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceSpy = {
      getToken: vi.fn(),
      clearSession: vi.fn()
    };
    routerSpy = {
      navigate: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should add Authorization Bearer header when token is available on protected endpoints', () => {
    authServiceSpy.getToken.mockReturnValue('valid-jwt-token');

    httpClient.get('/api/protected/resource').subscribe();

    const req = httpTesting.expectOne('/api/protected/resource');
    expect(req.request.headers.has('Authorization')).toBe(true);
    expect(req.request.headers.get('Authorization')).toBe('Bearer valid-jwt-token');
    req.flush({});
  });

  it('should NOT add Authorization header to login endpoint', () => {
    authServiceSpy.getToken.mockReturnValue('valid-jwt-token');

    httpClient.post('/api/auth/login', {}).subscribe();

    const req = httpTesting.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should clear session and navigate to /login when receiving 401 on protected route', () => {
    authServiceSpy.getToken.mockReturnValue('expired-jwt-token');

    httpClient.get('/api/protected/data').subscribe({
      error: () => {
        // Handled error
      }
    });

    const req = httpTesting.expectOne('/api/protected/data');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.clearSession).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
