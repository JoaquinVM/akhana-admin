import { Component, computed, effect, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashSessionSummary } from '../../../../core/cash/models/cash.models';

// CAJ-14: asks for the reason and says what reopening does to the closing and to Caja
@Component({
  selector: 'app-reopen-cash-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  templateUrl: './reopen-cash-modal.component.html',
  styleUrls: ['./reopen-cash-modal.component.css']
})
export class ReopenCashModalComponent {
  isOpen = input<boolean>(false);
  session = input<CashSessionSummary | null>(null);
  isLoading = input<boolean>(false);
  serverError = input<string | null>(null);

  close = output<void>();
  confirmed = output<string>();

  reason = signal<string>('');

  readonly canSubmit = computed(() => this.reason().trim().length > 0 && !this.isLoading());

  // What happens to the difference of the closing
  readonly adjustmentKind = computed(() => {
    const difference = this.session()?.difference ?? 0;
    return difference > 0 ? 'sobrante' : difference < 0 ? 'faltante' : null;
  });

  readonly adjustmentAmount = computed(() => Math.abs(this.session()?.difference ?? 0));

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.reason.set('');
      }
    });
  }

  onSubmit(): void {
    if (this.canSubmit()) {
      this.confirmed.emit(this.reason().trim());
    }
  }

  onCancel(): void {
    this.close.emit();
  }
}
