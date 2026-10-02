import { Component, inject, input, output, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { OpenCashRequest } from '../../../../core/cash/models/cash.models';

@Component({
  selector: 'app-open-cash-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent],
  templateUrl: './open-cash-modal.component.html',
  styleUrls: ['./open-cash-modal.component.css']
})
export class OpenCashModalComponent {
  private readonly fb = inject(FormBuilder);

  isOpen = input<boolean>(false);
  isLoading = input<boolean>(false);

  close = output<void>();
  confirmed = output<OpenCashRequest>();

  form: FormGroup = this.fb.group({
    openingAmount: [0, [Validators.required, Validators.min(0)]],
    openingComment: ['', [Validators.maxLength(500)]]
  });

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.form.reset({
          openingAmount: 0,
          openingComment: ''
        });
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.value;
    this.confirmed.emit({
      openingAmount: Number(val.openingAmount),
      openingComment: val.openingComment ? val.openingComment.trim() : null
    });
  }

  onCancel(): void {
    this.close.emit();
  }
}
