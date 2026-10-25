import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard, guestGuard, sessionGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('Auth Guards', () => {
  let authServiceSpy: { isAuthenticated: ReturnType<typeof vi.fn>; mustChangePassword: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceSpy = {
      isAuthenticated: vi.fn(),
      mustChangePassword: vi.fn().mockReturnValue(false)
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy }
      ]
    });
  });

  describe('authGuard', () => {
    it('should allow access when user is authenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(true);

      const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
      expect(result).toBe(true);
    });

    it('should redirect to /login when user is unauthenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(false);

      const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
      expect(result instanceof UrlTree).toBe(true);
      expect((result as UrlTree).toString()).toBe('/login');
    });
  });

  describe('guestGuard', () => {
    it('should allow access to login when user is unauthenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(false);

      const result = TestBed.runInInjectionContext(() => guestGuard({} as any, {} as any));
      expect(result).toBe(true);
    });

    it('should redirect to /pos when user is already authenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(true);

      const result = TestBed.runInInjectionContext(() => guestGuard({} as any, {} as any));
      expect(result instanceof UrlTree).toBe(true);
      expect((result as UrlTree).toString()).toBe('/pos');
    });
  });

  describe('password change required', () => {
    beforeEach(() => {
      authServiceSpy.isAuthenticated.mockReturnValue(true);
      authServiceSpy.mustChangePassword.mockReturnValue(true);
    });

    it('authGuard should redirect to /change-password', () => {
      const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
      expect((result as UrlTree).toString()).toBe('/change-password');
    });

    it('guestGuard should redirect to /change-password', () => {
      const result = TestBed.runInInjectionContext(() => guestGuard({} as any, {} as any));
      expect((result as UrlTree).toString()).toBe('/change-password');
    });

    it('sessionGuard should allow the change-password screen', () => {
      const result = TestBed.runInInjectionContext(() => sessionGuard({} as any, {} as any));
      expect(result).toBe(true);
    });
  });

  describe('sessionGuard', () => {
    it('should redirect to /login when user is unauthenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(false);

      const result = TestBed.runInInjectionContext(() => sessionGuard({} as any, {} as any));
      expect((result as UrlTree).toString()).toBe('/login');
    });
  });
});
