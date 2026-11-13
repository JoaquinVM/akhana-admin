import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginComponent, SESSION_ENDED } from './login.component';
import { AuthService } from '../../core/auth/auth.service';
import { LoginResponse } from '../../core/auth/models/auth.models';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: {
    login: ReturnType<typeof vi.fn>;
    loginWithSecondFactor: ReturnType<typeof vi.fn>;
    consumeSessionEnded?: ReturnType<typeof vi.fn>;
  };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authServiceSpy = {
      login: vi.fn(),
      loginWithSecondFactor: vi.fn()
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
      role: 'ADMINISTRATOR',
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
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
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

  it('should display the backend message when login is blocked (429)', () => {
    const message = 'Demasiados intentos fallidos. Espere 15 minutos e intente nuevamente.';
    const errorResponse = new HttpErrorResponse({ status: 429, error: { error: 'Too Many Requests', message } });
    authServiceSpy.login.mockReturnValue(throwError(() => errorResponse));

    component.loginForm.setValue({
      username: 'admin',
      password: 'any'
    });

    component.onSubmit();

    expect(component.errorMessage()).toBe(message);
    expect(component.loginForm.enabled).toBe(true);
  });

  it('should toggle password visibility flag', () => {
    expect(component.showPassword()).toBe(false);
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBe(true);
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBe(false);
  });

  it('should navigate to /change-password when the login requires a password change', () => {
    authServiceSpy.login.mockReturnValue(of({
      token: 'jwt.token',
      id: 'uuid-1',
      username: 'admin',
      role: 'ADMINISTRATOR',
      mustChangePassword: true,
      message: 'Debe cambiar su contraseña.'
    } satisfies LoginResponse));

    component.loginForm.setValue({ username: 'admin', password: 'initial-pass' });
    component.onSubmit();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('si la sesión anterior terminó, lo explica una sola vez al volver al login', async () => {
    authServiceSpy.consumeSessionEnded = vi.fn().mockReturnValueOnce(true).mockReturnValue(false);
    const again = TestBed.createComponent(LoginComponent);
    again.detectChanges();

    expect(again.componentInstance.errorMessage()).toBe(SESSION_ENDED);
    expect(again.nativeElement.textContent).toContain('Su sesión terminó');
    expect(TestBed.createComponent(LoginComponent).componentInstance.errorMessage()).toBeNull();
  });

  describe('segundo factor de administradores (USR-08)', () => {
    const SECRET = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
    const challenge = (setupRequired: boolean): LoginResponse => ({
      token: null,
      id: '',
      username: 'duena',
      role: 'ADMINISTRATOR',
      message: setupRequired ? 'Active el segundo factor con su app autenticadora.' : 'Ingrese el código de su app autenticadora.',
      twoFactor: {
        challengeId: 'c-1',
        setupRequired,
        secret: setupRequired ? SECRET : null,
        otpauthUri: setupRequired ? `otpauth://totp/Akhana%20Admin:duena?secret=${SECRET}&issuer=Akhana%20Admin` : null
      }
    });
    const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
    const passwordStep = (setupRequired: boolean) => {
      authServiceSpy.login.mockReturnValue(of(challenge(setupRequired)));
      component.loginForm.setValue({ username: 'duena', password: 'clave-duena' });
      component.onSubmit();
      fixture.detectChanges();
    };

    it('al activarlo muestra el QR, la clave en grupos de 4 y no entra todavía', async () => {
      passwordStep(true);

      expect(routerSpy.navigate).not.toHaveBeenCalled();
      await vi.waitFor(() => expect(component.qrDataUrl()).not.toBeNull());
      fixture.detectChanges();
      const qr = fixture.nativeElement.querySelector('img.qr-image') as HTMLImageElement;
      expect(qr.getAttribute('src')).toMatch(/^data:image\/gif;base64,/);
      expect(text()).toContain('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');
      expect(text()).toContain('Activar e ingresar');
      expect(fixture.nativeElement.querySelector('#password')).toBeNull();
    });

    it('con el 2FA activo pide solo el código, sin QR ni clave', () => {
      passwordStep(false);

      expect(fixture.nativeElement.querySelector('img.qr-image')).toBeNull();
      expect(text()).toContain('Código de verificación');
      expect(text()).not.toContain('JBSW');
    });

    it('envía el código sin espacios y entra', () => {
      passwordStep(false);
      authServiceSpy.loginWithSecondFactor.mockReturnValue(of({
        token: 'admin.token', id: 'u-1', username: 'duena', role: 'ADMINISTRATOR', message: 'Sesión iniciada.'
      } satisfies LoginResponse));

      component.codeForm.setValue({ code: '123 456' });
      component.onSubmitCode();

      expect(authServiceSpy.loginWithSecondFactor).toHaveBeenCalledWith({ challengeId: 'c-1', code: '123456' });
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
    });

    it('un código de menos de 6 dígitos no se envía', () => {
      passwordStep(false);
      component.codeForm.setValue({ code: '12345' });
      component.onSubmitCode();

      expect(authServiceSpy.loginWithSecondFactor).not.toHaveBeenCalled();
      expect(component.isCodeInvalid()).toBe(true);
    });

    it('con un código incorrecto sigue en el paso del código y lo dice', () => {
      passwordStep(false);
      authServiceSpy.loginWithSecondFactor.mockReturnValue(throwError(() =>
        new HttpErrorResponse({ status: 401, error: { message: 'Código incorrecto.' } })));

      component.codeForm.setValue({ code: '000000' });
      component.onSubmitCode();
      fixture.detectChanges();

      expect(component.secondFactor()).not.toBeNull();
      expect(component.errorMessage()).toBe('Código incorrecto.');
      expect(routerSpy.navigate).not.toHaveBeenCalled();
    });

    it('si el ingreso venció o quedó bloqueado, vuelve a la contraseña con el mensaje', () => {
      passwordStep(false);
      authServiceSpy.loginWithSecondFactor.mockReturnValue(throwError(() => new HttpErrorResponse({
        status: 401, error: { message: 'El ingreso venció. Vuelva a ingresar su usuario y contraseña.' }
      })));

      component.codeForm.setValue({ code: '123456' });
      component.onSubmitCode();
      fixture.detectChanges();

      expect(component.secondFactor()).toBeNull();
      expect(component.loginForm.getRawValue().password).toBe('');
      expect(text()).toContain('El ingreso venció. Vuelva a ingresar su usuario y contraseña.');
    });

    it('Volver regresa a la contraseña', () => {
      passwordStep(true);
      (fixture.nativeElement.querySelector('.link-button') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(component.secondFactor()).toBeNull();
      expect(fixture.nativeElement.querySelector('#password')).not.toBeNull();
    });
  });
});

