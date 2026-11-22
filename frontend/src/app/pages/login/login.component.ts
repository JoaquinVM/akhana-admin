import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginResponse, TwoFactorChallenge } from '../../core/auth/models/auth.models';

export const SESSION_ENDED = 'Tu sesión terminó: venció, se cambió la contraseña o el usuario fue dado de baja. Vuelve a ingresar.';
const WRONG_CODE = 'Código incorrecto.';
// 098: the server's answer to a wrong or expired activation code; the user may try again
const WRONG_ACTIVATION_CODE = 'Código de activación incorrecto o vencido. Pídele uno nuevo al administrador.';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly loginForm = this.fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  readonly codeForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\s*\d{3}\s?\d{3}\s*$/)]],
    // 098: the 8 digits an administrator handed in person, only when activating asks for them
    activationCode: ['']
  });

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);

  // Administrators: the second factor still to enter after the password (USR-08)
  readonly secondFactor = signal<TwoFactorChallenge | null>(null);
  // Drawn in the browser, so the secret is never sent to a QR service
  readonly qrDataUrl = signal<string | null>(null);
  // In groups of 4, to type it in the app when the QR code cannot be scanned (on the same phone)
  readonly secretGroups = computed(() => this.secondFactor()?.secret?.match(/.{1,4}/g)?.join(' ') ?? '');

  constructor() {
    if (this.authService.consumeSessionEnded()) {
      this.errorMessage.set(SESSION_ENDED);
    }
  }

  togglePasswordVisibility(): void {
    this.showPassword.update((val) => !val);
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.isLoading.set(true);
    this.loginForm.disable();

    const { username, password } = this.loginForm.getRawValue();

    this.authService.login({
      username: (username ?? '').trim(),
      password: password ?? ''
    }).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        this.loginForm.enable();
        if (response.twoFactor) {
          this.codeForm.reset();
          const activationCode = this.codeForm.get('activationCode');
          activationCode?.setValidators(response.twoFactor.activationCodeRequired
            ? [Validators.required, Validators.pattern(/^\s*\d{4}\s?\d{4}\s*$/)] : []);
          activationCode?.updateValueAndValidity();
          this.secondFactor.set(response.twoFactor);
          if (response.twoFactor.otpauthUri) {
            this.drawQr(response.twoFactor.otpauthUri);
          }
          return;
        }
        this.enter(response);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.loginForm.enable();
        if (err.status === 401) {
          this.errorMessage.set('Credenciales no válidas. Revisa tu usuario y contraseña.');
        } else if (err.status === 429) {
          this.errorMessage.set(err.error?.message ?? 'Demasiados intentos fallidos. Intenta más tarde.');
        } else if (err.status === 0 || err.error instanceof ProgressEvent) {
          this.errorMessage.set('No fue posible comunicarse con el servidor. Intenta nuevamente.');
        } else {
          this.errorMessage.set('Ocurrió un error al procesar el inicio de sesión. Intenta más tarde.');
        }
      }
    });
  }

  onSubmitCode(): void {
    const challenge = this.secondFactor();
    if (!challenge) return;
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.isLoading.set(true);
    this.codeForm.disable();
    const code = (this.codeForm.getRawValue().code ?? '').replace(/\s/g, '');
    const activationCode = challenge.activationCodeRequired
      ? (this.codeForm.getRawValue().activationCode ?? '').replace(/\s/g, '') : undefined;

    this.authService.loginWithSecondFactor({ challengeId: challenge.challengeId, code, activationCode }).subscribe({
      next: (response) => {
        this.isLoading.set(false);
        this.codeForm.enable();
        this.enter(response);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.codeForm.enable();
        this.codeForm.reset();
        if (err.status === 0 || err.error instanceof ProgressEvent) {
          this.errorMessage.set('No fue posible comunicarse con el servidor. Intenta nuevamente.');
        } else if (err.status === 401 && (err.error?.message === WRONG_CODE || err.error?.message === WRONG_ACTIVATION_CODE)) {
          this.errorMessage.set(err.error.message);
        } else {
          // The login ended (expired, too many codes or locked): it starts again from the password
          this.backToPassword();
          this.errorMessage.set(err.error?.message ?? 'El ingreso venció. Vuelve a ingresar tu usuario y contraseña.');
        }
      }
    });
  }

  // The QR library loads only when an administrator activates the second factor: it stays out of the initial bundle
  private async drawQr(uri: string): Promise<void> {
    const { default: qrcode } = await import('qrcode-generator');
    if (this.secondFactor()?.otpauthUri !== uri) return;
    const qr = qrcode(0, 'M');
    qr.addData(uri);
    qr.make();
    this.qrDataUrl.set(qr.createDataURL(5, 2));
  }

  backToPassword(): void {
    this.secondFactor.set(null);
    this.qrDataUrl.set(null);
    this.codeForm.reset();
    this.loginForm.patchValue({ password: '' });
    this.errorMessage.set(null);
  }

  isActivationCodeInvalid(): boolean {
    const field = this.codeForm.get('activationCode');
    return !!field && field.invalid && (field.dirty || field.touched);
  }

  isCodeInvalid(): boolean {
    const field = this.codeForm.get('code');
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  private enter(response: LoginResponse): void {
    this.router.navigate([response.mustChangePassword ? '/change-password' : '/']);
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.loginForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }
}
