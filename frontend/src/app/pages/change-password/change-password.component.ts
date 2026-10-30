import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';

const passwordsMatch = (group: AbstractControl): ValidationErrors | null =>
  group.get('newPassword')?.value === group.get('confirmPassword')?.value ? null : { mismatch: true };

/**
 * Minimal screen to change the password (mandatory on the first login of the initial admin).
 * The password rules are validated by the backend; here only required fields and confirmation.
 */
@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './change-password.component.html',
  styleUrls: ['../login/login.component.css', './change-password.component.css']
})
export class ChangePasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);

  readonly form = this.fb.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required]],
      confirmPassword: ['', [Validators.required]]
    },
    { validators: passwordsMatch }
  );

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.isLoading.set(true);
    this.form.disable();

    const { currentPassword, newPassword } = this.form.getRawValue();

    this.authService.changePassword({
      currentPassword: currentPassword ?? '',
      newPassword: newPassword ?? ''
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.form.enable();
        this.router.navigate(['/']);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.form.enable();
        if (err.status === 0) {
          this.errorMessage.set('No fue posible comunicarse con el servidor. Intente nuevamente.');
        } else {
          this.errorMessage.set(err.error?.message ?? 'No se pudo cambiar la contraseña. Intente más tarde.');
        }
      }
    });
  }

  logout(): void {
    this.authService.logout();
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.form.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  showMismatch(): boolean {
    const confirm = this.form.get('confirmPassword');
    return !!(this.form.hasError('mismatch') && confirm && (confirm.dirty || confirm.touched) && confirm.value);
  }
}
