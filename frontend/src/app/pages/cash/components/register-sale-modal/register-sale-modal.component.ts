import { Component, inject, input, output, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SaleRequest, PaymentMethod } from '../../../../core/cash/models/cash.models';

@Component({
  selector: 'app-register-sale-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent],
  templateUrl: './register-sale-modal.component.html',
  styleUrls: ['./register-sale-modal.component.css']
})
export class RegisterSaleModalComponent {
  private readonly fb = inject(FormBuilder);

  isOpen = input<boolean>(false);
  isLoading = input<boolean>(false);

  close = output<void>();
  confirmed = output<SaleRequest>();

  form: FormGroup = this.fb.group({
    totalAmount: [null, [Validators.required, Validators.min(0.01)]],
    paymentMethod: ['EFECTIVO', [Validators.required]],
    description: ['', [Validators.maxLength(255)]]
  });

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.form.reset({
          totalAmount: null,
          paymentMethod: 'EFECTIVO',
          description: ''
        });
      }
    });
  }

  setPaymentMethod(method: PaymentMethod): void {
    this.form.patchValue({ paymentMethod: method });
  }

  onSubmit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.value;
    this.confirmed.emit({
      totalAmount: Number(val.totalAmount),
      paymentMethod: val.paymentMethod as PaymentMethod,
      description: val.description ? val.description.trim() : null
    });
  }

  onCancel(): void {
    this.close.emit();
  }
}
