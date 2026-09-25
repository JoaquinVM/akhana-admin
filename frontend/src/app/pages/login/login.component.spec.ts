import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/auth/auth.service';
import { LoginResponse } from '../../core/auth/models/auth.models';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: { login: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authServiceSpy = {
      login: vi.fn()
    };
    routerSpy = {
      navigate: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and have initial invalid empty form', () => {
    expect(component).toBeTruthy();
    expect(component.loginForm.valid).toBe(false);
  });

  it('should not call authService.login if form is submitted empty', () => {
    component.onSubmit();
    expect(authServiceSpy.login).not.toHaveBeenCalled();
    expect(component.isFieldInvalid('username')).toBe(true);
    expect(component.isFieldInvalid('password')).toBe(true);
  });

  it('should call authService.login and navigate to /pos on success', () => {
    const mockResponse: LoginResponse = {
      token: 'jwt.token',
      id: 'uuid-1',
      username: 'admin',
      role: 'ADMIN',
      message: 'OK'
    };
    authServiceSpy.login.mockReturnValue(of(mockResponse));

    component.loginForm.setValue({
      username: 'admin',
      password: 'password123'
    });

    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      username: 'admin',
      password: 'password123'
    });
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/pos']);
    expect(component.isLoading()).toBe(false);
  });

  it('should display error message when credentials are invalid (401)', () => {
    const errorResponse = new HttpErrorResponse({ status: 401 });
    authServiceSpy.login.mockReturnValue(throwError(() => errorResponse));

    component.loginForm.setValue({
      username: 'admin',
      password: 'wrong'
    });

    component.onSubmit();

    expect(component.errorMessage()).toBe('Credenciales no válidas. Verifique su usuario y contraseña.');
    expect(component.isLoading()).toBe(false);
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should display server error message when backend is unreachable (status 0)', () => {
    const errorResponse = new HttpErrorResponse({ status: 0 });
    authServiceSpy.login.mockReturnValue(throwError(() => errorResponse));

    component.loginForm.setValue({
      username: 'admin',
      password: 'any'
    });

    component.onSubmit();

    expect(component.errorMessage()).toBe('No fue posible comunicarse con el servidor. Intente nuevamente.');
  });

  it('should toggle password visibility flag', () => {
    expect(component.showPassword()).toBe(false);
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBe(true);
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBe(false);
  });
});
