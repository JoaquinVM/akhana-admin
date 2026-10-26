import { Component, inject, input, output, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashService } from '../../../../core/cash/cash.service';
import { CashOpeningProposal, OpenCashRequest } from '../../../../core/cash/models/cash.models';

@Component({
  selector: 'app-open-cash-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent],
  templateUrl: './open-cash-modal.component.html',
  styleUrls: ['./open-cash-modal.component.css']
})
export class OpenCashModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly cashService = inject(CashService);

  isOpen = input<boolean>(false);
  isLoading = input<boolean>(false);

  close = output<void>();
  confirmed = output<OpenCashRequest>();

  // CAJ-10: cierre de la caja anterior; null mientras carga o si no hay caja anterior
  proposal = signal<CashOpeningProposal | null>(null);
  amountChanged = signal<boolean>(false);

  form: FormGroup = this.fb.group({
    openingAmount: [0, [Validators.required, Validators.min(0)]],
    openingComment: ['', [Validators.maxLength(500)]]
  });

  private readonly openingAmount = toSignal(this.form.get('openingAmount')!.valueChanges, { initialValue: 0 });

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.proposal.set(null);
        this.form.reset({ openingAmount: 0, openingComment: '' });
        this.cashService.getOpeningProposal().subscribe({
          next: (proposal) => {
            if (proposal.previousSessionNumber === null) return;
            this.proposal.set(proposal);
            this.form.patchValue({ openingAmount: proposal.proposedAmount });
          }
        });
      }
    });

    // El comentario pasa a ser obligatorio si el monto difiere del cierre anterior (el backend lo valida igual)
    effect(() => {
      const amount = this.openingAmount();
      const proposal = this.proposal();
      const changed = proposal !== null && amount !== null && amount !== ''
        && Math.round(Number(amount) * 100) !== Math.round(proposal.proposedAmount * 100);
      this.amountChanged.set(changed);
      const comment = this.form.get('openingComment')!;
      comment.setValidators(changed
        ? [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]
        : [Validators.maxLength(500)]);
      comment.updateValueAndValidity({ emitEvent: false });
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
