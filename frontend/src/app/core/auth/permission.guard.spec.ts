import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { permissionGuard } from './permission.guard';
import { AuthService } from './auth.service';

describe('permissionGuard', () => {
  let authServiceSpy: {
    isAuthenticated: ReturnType<typeof vi.fn>;
    hasPermission: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authServiceSpy = {
      isAuthenticated: vi.fn(),
      hasPermission: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy }
      ]
    });

    router = TestBed.inject(Router);
  });

  it('debe redirigir a /login si el usuario no está autenticado', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);

    const route = { data: { permission: 'SECURITY_USERS' } } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/users' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => permissionGuard(route, state));
    expect(result instanceof UrlTree).toBe(true);
    expect((result as UrlTree).toString()).toBe('/login');
  });

  it('debe permitir acceso si la ruta no exige permiso específico', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);

    const route = { data: {} } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/any' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => permissionGuard(route, state));
    expect(result).toBe(true);
  });

  it('debe permitir acceso si el usuario cuenta con el permiso requerido', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    authServiceSpy.hasPermission.mockImplementation((p: string) => p === 'SECURITY_USERS');

    const route = { data: { permission: 'SECURITY_USERS' } } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/users' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => permissionGuard(route, state));
    expect(result).toBe(true);
    expect(authServiceSpy.hasPermission).toHaveBeenCalledWith('SECURITY_USERS');
  });

  it('debe redirigir a la primera ruta permitida si no tiene el permiso de la ruta solicitada', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    // No tiene SECURITY_USERS, pero tiene CATALOG_PRODUCTS
    authServiceSpy.hasPermission.mockImplementation((p: string) => p === 'CATALOG_PRODUCTS');

    const route = { data: { permission: 'SECURITY_USERS' } } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/users' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => permissionGuard(route, state));
    expect(result instanceof UrlTree).toBe(true);
    expect((result as UrlTree).toString()).toBe('/products');
  });

  it('debe redirigir a /unauthorized si el usuario no tiene ningún permiso en el sistema', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    authServiceSpy.hasPermission.mockReturnValue(false);

    const route = { data: { permission: 'SECURITY_USERS' } } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/users' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => permissionGuard(route, state));
    expect(result instanceof UrlTree).toBe(true);
    expect((result as UrlTree).toString()).toBe('/unauthorized');
  });
});
