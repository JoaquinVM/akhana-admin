import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HttpErrorResponse } from '@angular/common/http';

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

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);

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
      next: () => {
        this.isLoading.set(false);
        this.loginForm.enable();
        this.router.navigate(['/pos']);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.loginForm.enable();
        if (err.status === 401) {
          this.errorMessage.set('Credenciales no válidas. Verifique su usuario y contraseña.');
        } else if (err.status === 0 || err.error instanceof ProgressEvent) {
          this.errorMessage.set('No fue posible comunicarse con el servidor. Intente nuevamente.');
        } else {
          this.errorMessage.set('Ocurrió un error al procesar el inicio de sesión. Intente más tarde.');
        }
      }
    });
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.loginForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }
}
