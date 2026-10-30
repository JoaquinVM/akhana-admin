import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangePasswordComponent } from './change-password.component';
import { AuthService } from '../../core/auth/auth.service';

describe('ChangePasswordComponent', () => {
  let component: ChangePasswordComponent;
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let authServiceSpy: {
    changePassword: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
    mustChangePassword: ReturnType<typeof signal<boolean>>;
  };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authServiceSpy = {
      changePassword: vi.fn(),
      logout: vi.fn(),
      mustChangePassword: signal(true)
    };
    routerSpy = { navigate: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChangePasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should not submit an empty form', () => {
    component.onSubmit();
    expect(authServiceSpy.changePassword).not.toHaveBeenCalled();
    expect(component.isFieldInvalid('currentPassword')).toBe(true);
  });

  it('should not submit when the confirmation does not match', () => {
    component.form.setValue({ currentPassword: 'initial-pass', newPassword: 'new-strong-pass', confirmPassword: 'other-pass' });
    component.form.markAllAsTouched();

    component.onSubmit();

    expect(authServiceSpy.changePassword).not.toHaveBeenCalled();
    expect(component.showMismatch()).toBe(true);
  });

  it('should change the password and navigate to /pos', () => {
    authServiceSpy.changePassword.mockReturnValue(of(undefined));
    component.form.setValue({ currentPassword: 'initial-pass', newPassword: 'new-strong-pass', confirmPassword: 'new-strong-pass' });

    component.onSubmit();

    expect(authServiceSpy.changePassword).toHaveBeenCalledWith({ currentPassword: 'initial-pass', newPassword: 'new-strong-pass' });
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('should show the backend validation message', () => {
    authServiceSpy.changePassword.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400,
      error: { message: 'La contraseña actual no es correcta' }
    })));
    component.form.setValue({ currentPassword: 'wrong', newPassword: 'new-strong-pass', confirmPassword: 'new-strong-pass' });

    component.onSubmit();

    expect(component.errorMessage()).toBe('La contraseña actual no es correcta');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should log out', () => {
    component.logout();
    expect(authServiceSpy.logout).toHaveBeenCalled();
  });
});
